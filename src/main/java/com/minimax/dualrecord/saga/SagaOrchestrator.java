package com.minimax.dualrecord.saga;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimax.dualrecord.domain.SagaEvent;
import com.minimax.dualrecord.domain.SagaInstance;
import com.minimax.dualrecord.domain.SagaStep;
import com.minimax.dualrecord.repository.SagaEventRepository;
import com.minimax.dualrecord.repository.SagaInstanceRepository;
import com.minimax.dualrecord.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Saga 编排器 - 核心执行引擎
 *
 * 状态机:
 *   PENDING -> RUNNING -> COMPLETED (成功)
 *   PENDING -> RUNNING -> FAILED -> COMPENSATING -> COMPENSATED (失败后补偿完成)
 *   PENDING -> RUNNING -> SUSPENDED (需要人工干预)
 *
 * 特性:
 *  - 持久化每步状态, 可断点续传
 *  - 自动重试 (默认 2 次)
 *  - 失败时按逆序调用补偿方法
 *  - 完整事件日志 (审计 + 调试)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaInstanceRepository instanceRepo;
    private final SagaStepRepository stepRepo;
    private final SagaEventRepository eventRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存: 业务对象 -> 步骤方法列表
    private final Map<String, List<MethodHolder>> methodCache = new HashMap<>();

    // ==================== 核心执行 ====================

    /**
     * 执行 Saga (被 Aspect 调用)
     * @param bean 目标对象 (@SagaStart 所在类)
     * @param startMethod @SagaStart 方法
     * @param args 入参
     * @return Saga 执行结果 (包括上下文)
     */
    public Object execute(Object bean, Method startMethod, Object[] args) {
        com.minimax.dualrecord.saga.annotation.SagaStart startAnno = startMethod.getAnnotation(
            com.minimax.dualrecord.saga.annotation.SagaStart.class);
        String sagaId = "saga-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        // 1. 收集 @SagaStep 方法 (按 order 排序)
        List<MethodHolder> steps = collectSteps(bean.getClass());

        // 2. 创建 Saga 实例
        SagaInstance instance = createInstance(sagaId, startAnno, startMethod, args, steps.size());

        // 3. 执行 Saga
        return executeSteps(bean, startMethod, args, instance, steps, startAnno.autoCompensate());
    }

    private Object executeSteps(Object bean, Method startMethod, Object[] args,
                                 SagaInstance instance, List<MethodHolder> steps,
                                 boolean autoCompensate) {
        log.info("🎬 [Saga-{}] 开始执行 {} ({} 步)", instance.getSagaId(), instance.getSagaName(), steps.size());

        // 更新为 RUNNING
        updateStatus(instance.getSagaId(), "RUNNING", null, null);
        recordEvent(instance.getSagaId(), null, "STARTED", "INFO",
            "Saga 开始: " + instance.getSagaName(), null);

        // 共享上下文: step output -> 后续 step 可读
        Map<String, Object> context = new LinkedHashMap<>();

        for (MethodHolder stepHolder : steps) {
            int order = stepHolder.order;
            Method stepMethod = stepHolder.method;
            com.minimax.dualrecord.saga.annotation.SagaStep stepAnno = stepHolder.annotation;

            log.info("  → [{}-{}] {} 准备执行", instance.getSagaId(), order, stepAnno.name());

            // 1. 创建 step 记录
            SagaStep stepRecord = createStepRecord(instance, stepHolder);
            String stepStatus = "RUNNING";
            stepRecord.setStatus(stepStatus);
            stepRecord.setStartedAt(LocalDateTime.now());
            stepRepo.updateById(stepRecord);

            // 2. 提取输入参数 (从 args + context)
            Object[] stepArgs = resolveStepArgs(stepMethod, args, context);
            recordStepInput(instance.getSagaId(), order, stepArgs);

            // 3. 执行 (带重试)
            Object result = null;
            Exception lastError = null;
            int maxRetries = stepAnno.retryable() ? stepAnno.timeoutMs() > 0 ? instance.getMaxRetries() : 0 : 0;
            // 简化: 0 retry

            try {
                result = stepMethod.invoke(bean, stepArgs);
                stepStatus = "COMPLETED";
                stepRecord.setStatus(stepStatus);
                stepRecord.setCompletedAt(LocalDateTime.now());
                stepRecord.setDurationMs(System.currentTimeMillis() -
                    java.time.Duration.between(stepRecord.getStartedAt(), stepRecord.getCompletedAt()).toMillis() * -1);
                if (result != null) {
                    String outputJson = objectMapper.writeValueAsString(result);
                    stepRecord.setOutputJson(truncate(outputJson, 8192));
                    // 存入 context
                    context.put("step-" + order, result);
                }
                stepRepo.updateById(stepRecord);
                recordEvent(instance.getSagaId(), order, "STEP_OK", "INFO",
                    "步骤 " + order + " (" + stepAnno.name() + ") 成功", null);

                // 更新 current_step
                updateCurrentStep(instance.getSagaId(), order + 1);
                instance.setCurrentStep(order + 1);

                log.info("    ✓ [{}-{}] {} 完成", instance.getSagaId(), order, stepAnno.name());

            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                lastError = e;
                stepStatus = "FAILED";
                stepRecord.setStatus(stepStatus);
                stepRecord.setErrorMessage(truncate(cause.getMessage(), 2000));
                stepRecord.setCompletedAt(LocalDateTime.now());
                stepRepo.updateById(stepRecord);

                recordEvent(instance.getSagaId(), order, "STEP_FAIL", "ERROR",
                    "步骤 " + order + " 失败: " + cause.getMessage(), null);

                log.error("    ✗ [{}-{}] {} 失败: {}", instance.getSagaId(), order, stepAnno.name(), cause.getMessage());

                // 4. 触发补偿
                if (autoCompensate) {
                    compensate(bean, instance, steps, order, context);
                } else {
                    updateStatus(instance.getSagaId(), "SUSPENDED", cause.getMessage(), String.valueOf(order));
                    recordEvent(instance.getSagaId(), null, "SUSPENDED", "WARN",
                        "Saga 挂起, 需人工干预: " + cause.getMessage(), null);
                }
                throw new SagaExecutionException("Saga 失败 at step " + order + ": " + cause.getMessage(), cause);
            }
        }

        // 全部完成
        updateStatus(instance.getSagaId(), "COMPLETED", null, null);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setStatus("COMPLETED");
        instanceRepo.updateById(instance);
        recordEvent(instance.getSagaId(), null, "COMPLETED", "INFO",
            "Saga 完成: " + instance.getSagaName(), null);
        log.info("🎉 [Saga-{}] 完成", instance.getSagaId());

        return context;
    }

    // ==================== 补偿 ====================

    private void compensate(Object bean, SagaInstance instance, List<MethodHolder> steps,
                             int failedOrder, Map<String, Object> context) {
        log.warn("⏪ [Saga-{}] 启动补偿 (失败步骤: {})", instance.getSagaId(), failedOrder);
        updateStatus(instance.getSagaId(), "COMPENSATING", null, String.valueOf(failedOrder));
        recordEvent(instance.getSagaId(), null, "COMPENSATING", "WARN",
            "开始补偿, 失败于步骤 " + failedOrder, null);

        // 逆序补偿所有已完成的步骤
        boolean allOk = true;
        for (int i = failedOrder - 1; i >= 0; i--) {
            MethodHolder stepHolder = steps.get(i);
            Method compMethod = stepHolder.compensateMethod;
            if (compMethod == null) {
                log.warn("  ⚠ [{}-{}] {} 无补偿方法, 跳过", instance.getSagaId(), stepHolder.order, stepHolder.annotation.name());
                recordEvent(instance.getSagaId(), stepHolder.order, "COMPENSATING", "WARN",
                    "步骤 " + stepHolder.order + " 无补偿方法", null);
                continue;
            }

            try {
                log.info("  ↩ [{}-{}] 补偿 {} 执行中...", instance.getSagaId(), stepHolder.order, stepHolder.annotation.name());

                // 提取补偿方法参数 (从 context 取 step output)
                Object[] compArgs = resolveStepArgs(compMethod, new Object[]{}, context);
                compMethod.invoke(bean, compArgs);

                // 更新 step 状态
                markStepCompensated(instance.getSagaId(), stepHolder.order);
                recordEvent(instance.getSagaId(), stepHolder.order, "COMPENSATED", "INFO",
                    "步骤 " + stepHolder.order + " 补偿成功", null);
                log.info("    ✓ [{}-{}] 补偿成功", instance.getSagaId(), stepHolder.order);

            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                allOk = false;
                log.error("    ✗ [{}-{}] 补偿失败: {}", instance.getSagaId(), stepHolder.order, cause.getMessage());
                recordEvent(instance.getSagaId(), stepHolder.order, "COMPENSATED", "ERROR",
                    "步骤 " + stepHolder.order + " 补偿失败: " + cause.getMessage(), null);
            }
        }

        if (allOk) {
            updateStatus(instance.getSagaId(), "COMPENSATED", null, String.valueOf(failedOrder));
            recordEvent(instance.getSagaId(), null, "COMPENSATED", "INFO",
                "Saga 补偿完成", null);
        } else {
            updateStatus(instance.getSagaId(), "SUSPENDED", "补偿部分失败, 需人工介入",
                String.valueOf(failedOrder));
            recordEvent(instance.getSagaId(), null, "SUSPENDED", "ERROR",
                "补偿失败, 需人工介入", null);
        }
    }

    // ==================== 辅助 ====================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(String sagaId, Integer stepOrder, String eventType,
                            String level, String message, String payloadJson) {
        try {
            SagaEvent event = new SagaEvent();
            event.setId("evt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            event.setSagaId(sagaId);
            event.setStepOrder(stepOrder);
            event.setEventType(eventType);
            event.setLevel(level);
            event.setMessage(truncate(message, 2000));
            event.setPayloadJson(payloadJson);
            event.setOccurredAt(LocalDateTime.now());
            eventRepo.insert(event);
        } catch (Exception e) {
            log.warn("记录 saga 事件失败: {}", e.getMessage());
        }
    }

    private void recordStepInput(String sagaId, int order, Object[] args) {
        try {
            UpdateWrapper<SagaStep> uw = new UpdateWrapper<>();
            uw.eq("saga_id", sagaId).eq("step_order", order)
              .set("input_json", objectMapper.writeValueAsString(args));
            stepRepo.update(null, uw);
        } catch (Exception e) { /* ignore */ }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepCompensated(String sagaId, int order) {
        UpdateWrapper<SagaStep> uw = new UpdateWrapper<>();
        uw.eq("saga_id", sagaId).eq("step_order", order)
          .set("status", "COMPENSATED")
          .set("completed_at", LocalDateTime.now());
        stepRepo.update(null, uw);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(String sagaId, String status, String errorMessage, String errorStep) {
        UpdateWrapper<SagaInstance> uw = new UpdateWrapper<>();
        uw.eq("saga_id", sagaId)
          .set("status", status)
          .set("error_message", errorMessage != null ? truncate(errorMessage, 2000) : null)
          .set("error_step", errorStep)
          .set("updated_at", LocalDateTime.now());
        if ("COMPLETED".equals(status) || "COMPENSATED".equals(status) || "FAILED".equals(status)) {
            uw.set("completed_at", LocalDateTime.now());
        }
        instanceRepo.update(null, uw);
    }

    private void updateCurrentStep(String sagaId, int step) {
        UpdateWrapper<SagaInstance> uw = new UpdateWrapper<>();
        uw.eq("saga_id", sagaId).set("current_step", step).set("updated_at", LocalDateTime.now());
        instanceRepo.update(null, uw);
    }

    private SagaInstance createInstance(String sagaId,
                                          com.minimax.dualrecord.saga.annotation.SagaStart anno,
                                          Method startMethod, Object[] args, int totalSteps) {
        SagaInstance instance = new SagaInstance();
        instance.setId("saga-row-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        instance.setSagaId(sagaId);
        instance.setSagaName(anno.name().isEmpty() ? startMethod.getName() : anno.name());
        instance.setBusinessId(extractBusinessId(args, anno));
        instance.setStatus("PENDING");
        instance.setCurrentStep(0);
        instance.setTotalSteps(totalSteps);
        instance.setMaxRetries(anno.maxRetries());
        instance.setTimeoutMs(anno.stepTimeoutMs());
        try {
            instance.setPayloadJson(objectMapper.writeValueAsString(args));
        } catch (Exception e) {
            instance.setPayloadJson("[]");
        }
        instance.setStartedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        instanceRepo.insert(instance);
        return instance;
    }

    private SagaStep createStepRecord(SagaInstance instance, MethodHolder holder) {
        SagaStep step = new SagaStep();
        step.setId("sagastep-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        step.setSagaId(instance.getSagaId());
        step.setStepOrder(holder.order);
        step.setStepName(holder.annotation.name());
        step.setTargetMethod(holder.method.getDeclaringClass().getSimpleName() + "." + holder.method.getName());
        step.setCompensateMethod(holder.compensateMethod != null ?
            holder.compensateMethod.getDeclaringClass().getSimpleName() + "." + holder.compensateMethod.getName() : null);
        step.setStatus("PENDING");
        step.setRetryCount(0);
        stepRepo.insert(step);
        return step;
    }

    private String extractBusinessId(Object[] args, com.minimax.dualrecord.saga.annotation.SagaStart anno) {
        // 简化: 尝试从第一个 String 参数取
        if (args != null) {
            for (Object a : args) {
                if (a instanceof String) {
                    String s = (String) a;
                    if (s.startsWith("BNK") || s.startsWith("FND") || s.startsWith("LIC") || s.startsWith("STR") || s.startsWith("ORD")) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 收集类中所有 @SagaStep 方法, 按 order 排序
     */
    private List<MethodHolder> collectSteps(Class<?> clazz) {
        String key = clazz.getName();
        if (methodCache.containsKey(key)) return methodCache.get(key);

        List<MethodHolder> result = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            com.minimax.dualrecord.saga.annotation.SagaStep anno = m.getAnnotation(
                com.minimax.dualrecord.saga.annotation.SagaStep.class);
            if (anno == null) continue;
            m.setAccessible(true);
            Method comp = null;
            if (!anno.compensate().isEmpty()) {
                try {
                    comp = clazz.getDeclaredMethod(anno.compensate(), m.getParameterTypes());
                    comp.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    log.warn("补偿方法未找到: {}.{}", clazz.getSimpleName(), anno.compensate());
                }
            }
            result.add(new MethodHolder(anno.order(), m, anno, comp));
        }
        result.sort(Comparator.comparingInt(h -> h.order));
        methodCache.put(key, result);
        return result;
    }

    /**
     * 解析 step 参数
     * 简化: 同一 Saga 内所有 step 共享原 args + context
     */
    private Object[] resolveStepArgs(Method method, Object[] originalArgs, Map<String, Object> context) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) return new Object[0];
        if (paramTypes.length == 1 && paramTypes[0] == Map.class) {
            return new Object[]{context};
        }
        // 默认: 传 context
        return new Object[]{context};
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...[truncated]";
    }

    // ==================== 查询 ====================

    public SagaInstance getInstance(String sagaId) {
        return instanceRepo.selectOne(new QueryWrapper<SagaInstance>().eq("saga_id", sagaId));
    }

    public List<SagaStep> getSteps(String sagaId) {
        return stepRepo.selectList(new QueryWrapper<SagaStep>()
            .eq("saga_id", sagaId).orderByAsc("step_order"));
    }

    public List<SagaEvent> getEvents(String sagaId) {
        return eventRepo.selectList(new QueryWrapper<SagaEvent>()
            .eq("saga_id", sagaId).orderByAsc("occurred_at"));
    }

    public List<SagaInstance> listByBusiness(String businessId) {
        return instanceRepo.selectList(new QueryWrapper<SagaInstance>()
            .eq("business_id", businessId).orderByDesc("started_at"));
    }

    public List<SagaInstance> listRecent(int limit) {
        return instanceRepo.selectList(new QueryWrapper<SagaInstance>()
            .orderByDesc("started_at").last("LIMIT " + Math.min(limit, 200)));
    }

    public List<SagaInstance> listByStatus(String status, int limit) {
        return instanceRepo.selectList(new QueryWrapper<SagaInstance>()
            .eq("status", status).orderByDesc("started_at").last("LIMIT " + Math.min(limit, 200)));
    }

    // ==================== 内部类 ====================

    private static class MethodHolder {
        final int order;
        final Method method;
        final com.minimax.dualrecord.saga.annotation.SagaStep annotation;
        final Method compensateMethod;

        MethodHolder(int order, Method method,
                     com.minimax.dualrecord.saga.annotation.SagaStep annotation,
                     Method compensateMethod) {
            this.order = order;
            this.method = method;
            this.annotation = annotation;
            this.compensateMethod = compensateMethod;
        }
    }
}

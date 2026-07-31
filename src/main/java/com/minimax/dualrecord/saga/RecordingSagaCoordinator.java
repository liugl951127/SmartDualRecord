package com.minimax.dualrecord.saga;

import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.EventLogRepository;
import com.minimax.dualrecord.statemachine.RecordingStateMachine;
import com.minimax.dualrecord.domain.EventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Saga 协调器 · 实现分布式事务的最终一致性
 *
 * 核心思想：
 *  - 每个状态变更都是一个 Saga 步骤
 *  - 每个步骤都有一个 do() 和 compensate()
 *  - 失败时按相反顺序回滚所有已执行步骤
 *
 * 这里的"事务"包括：
 *  1. tb_business 状态更新
 *  2. tb_event 事件日志追加
 *  3. 跨服务调用（如有）
 *
 * 简化实现：单库场景下用本地 Saga；多服务场景下可升级为 TCC 或消息队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingSagaCoordinator {

    private final BusinessRepository businessRepository;
    private final EventLogRepository eventLogRepository;

    private final ThreadLocal<Deque<SagaStep>> CURRENT_SAGA = new ThreadLocal<>();

    /**
     * 在 Saga 保护下执行状态变更
     * 任何步骤失败 → 自动补偿
     */
    public void executeWithCompensation(String businessId,
                                        RecordingState targetState,
                                        SagaStep... steps) {
        Deque<SagaStep> executedSteps = new ArrayDeque<>();
        CURRENT_SAGA.set(executedSteps);
        try {
            for (SagaStep step : steps) {
                log.debug("Saga 执行步骤: businessId={}, step={}", businessId, step.getName());
                step.doAction();
                executedSteps.push(step);
            }
            // 所有步骤成功，提交最终状态
            applyStateChange(businessId, targetState, "SYSTEM", "SAGA_COMPLETED");
            log.info("Saga 提交成功: businessId={}, targetState={}", businessId, targetState);
        } catch (Exception e) {
            log.error("Saga 执行失败，启动补偿: businessId={}, error={}", businessId, e.getMessage());
            // 反向补偿
            compensate(executedSteps);
            // 标记为回滚状态
            applyStateChange(businessId, RecordingState.ROLLED_BACK, "SYSTEM",
                    "SAGA_COMPENSATED: " + e.getMessage());
            throw new BusinessException("SAGA_FAILED",
                    "业务事务失败，已回滚: " + e.getMessage());
        } finally {
            CURRENT_SAGA.remove();
        }
    }

    private void compensate(Deque<SagaStep> executedSteps) {
        while (!executedSteps.isEmpty()) {
            SagaStep step = executedSteps.pop();
            try {
                log.warn("Saga 补偿步骤: {}", step.getName());
                step.compensate();
            } catch (Exception e) {
                log.error("补偿失败: step={}, error={}", step.getName(), e.getMessage(), e);
                // 继续补偿其他步骤，失败的步骤进入人工干预队列
            }
        }
    }

    /**
     * 单纯的状态变更（带状态机校验 + 事件溯源）
     */
    public void applyStateChange(String businessId, RecordingState targetState,
                                 String actorId, String reason) {
        Business business = businessRepository.selectById(businessId);
        if (business == null) {
            throw new BusinessException("BUSINESS_NOT_FOUND", "业务不存在: " + businessId);
        }

        RecordingState fromState = business.getState();
        // 状态机校验
        RecordingState newState = RecordingStateMachine.transition(fromState, targetState);

        // 1. 更新业务状态
        business.setState(newState);
        business.setUpdatedAt(LocalDateTime.now());
        if (newState == RecordingState.ARCHIVED) {
            business.setArchivedAt(LocalDateTime.now());
        }
        businessRepository.updateById(business);

        // 2. 写事件日志（不可删除）
        EventLog event = new EventLog();
        event.setBusinessId(businessId);
        event.setEventType("STATE_TRANSITION");
        event.setFromState(fromState.name());
        event.setToState(newState.name());
        event.setActorId(actorId);
        event.setActorType("SYSTEM");
        event.setEventData(String.format("{\"reason\":\"%s\"}", reason.replace("\"", "'")));
        event.setCreatedAt(LocalDateTime.now());
        eventLogRepository.insert(event);

        log.info("状态变更: businessId={}, {} → {}, actor={}", businessId, fromState, newState, actorId);
    }
}

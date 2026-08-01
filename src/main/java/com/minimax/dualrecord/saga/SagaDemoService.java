package com.minimax.dualrecord.saga;

import com.minimax.dualrecord.saga.annotation.SagaStart;
import com.minimax.dualrecord.saga.annotation.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Saga 演示服务
 * 模拟 4 步: 创建业务 -> 创建录像 -> 创建事件 -> 创建风险
 * 任一步失败, 自动反向补偿
 */
@Service
@Slf4j
public class SagaDemoService {

    @Autowired
    private SagaFaultInjector faultInjector;

    /**
     * Saga 入口
     */
    @SagaStart(name = "CREATE_BUSINESS_DEMO", businessIdParam = "businessId",
               stepTimeoutMs = 5000, maxRetries = 1, autoCompensate = true)
    public Map<String, Object> createBusiness(Map<String, Object> params) {
        // Saga 入口不会真正执行 - Aspect 拦截后会调用 @SagaStep 方法
        // 但 AOP 模式下, 这里返回只是占位
        log.info("Saga 入口被调用, 但实际由 Aspect + 编排器接管");
        return new HashMap<>();
    }

    // ==================== Step 1: 创建业务 ====================
    @SagaStep(order = 0, name = "创建业务", compensate = "compensateCreateBusiness")
    public Map<String, Object> createBusinessStep(Map<String, Object> context) {
        log.info("  → Step 1: 创建业务 (simulated)");
        faultInjector.maybeFail("createBusiness");
        Map<String, Object> result = new HashMap<>();
        result.put("businessId", "BIZ-" + System.currentTimeMillis());
        result.put("status", "INIT");
        result.put("amount", context.get("amount"));
        return result;
    }

    public void compensateCreateBusiness(Map<String, Object> context) {
        log.info("  ↩ 补偿 Step 1: 撤销业务创建");
    }

    // ==================== Step 2: 创建录像 ====================
    @SagaStep(order = 1, name = "创建录像", compensate = "compensateCreateRecording")
    public Map<String, Object> createRecordingStep(Map<String, Object> context) {
        log.info("  → Step 2: 创建录像 (simulated)");
        faultInjector.maybeFail("createRecording");
        Map<String, Object> result = new HashMap<>();
        result.put("recId", "REC-" + System.currentTimeMillis());
        return result;
    }

    public void compensateCreateRecording(Map<String, Object> context) {
        log.info("  ↩ 补偿 Step 2: 删除录像");
    }

    // ==================== Step 3: 初始化事件 ====================
    @SagaStep(order = 2, name = "写初始事件", compensate = "compensateInitEvent")
    public Map<String, Object> initEventStep(Map<String, Object> context) {
        log.info("  → Step 3: 写初始事件 (simulated)");
        faultInjector.maybeFail("initEvent");
        Map<String, Object> result = new HashMap<>();
        result.put("eventId", "EVT-" + System.currentTimeMillis());
        return result;
    }

    public void compensateInitEvent(Map<String, Object> context) {
        log.info("  ↩ 补偿 Step 3: 删除事件");
    }

    // ==================== Step 4: 风评快照 ====================
    @SagaStep(order = 3, name = "风评快照", compensate = "compensateRiskSnapshot")
    public Map<String, Object> riskSnapshotStep(Map<String, Object> context) {
        log.info("  → Step 4: 风评快照 (simulated)");
        faultInjector.maybeFail("riskSnapshot");
        Map<String, Object> result = new HashMap<>();
        result.put("riskId", "RISK-" + System.currentTimeMillis());
        return result;
    }

    public void compensateRiskSnapshot(Map<String, Object> context) {
        log.info("  ↩ 补偿 Step 4: 撤销风评快照");
    }
}

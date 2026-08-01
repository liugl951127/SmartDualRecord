package com.minimax.dualrecord.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Saga 故障注入器 - 单元测试用
 * 通过 HTTP 接口控制哪个步骤注入失败
 */
@Component
@Slf4j
public class SagaFaultInjector {

    // 步骤名 -> 失败次数
    private final ConcurrentHashMap<String, AtomicInteger> faultMap = new ConcurrentHashMap<>();

    // 全局开关
    private volatile boolean globalEnabled = false;

    public void injectFault(String stepName, int count) {
        faultMap.computeIfAbsent(stepName, k -> new AtomicInteger()).set(count);
        log.warn("💉 注入故障: step={} count={}", stepName, count);
    }

    public void clear() {
        faultMap.clear();
        globalEnabled = false;
        log.info("💉 清除所有故障注入");
    }

    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
        log.info("💉 全局故障: {}", enabled ? "ON" : "OFF");
    }

    /**
     * 步骤执行前调用
     * 如果该步骤还有剩余失败次数, 抛异常
     */
    public void maybeFail(String stepName) {
        if (globalEnabled && !faultMap.containsKey(stepName)) {
            throw new RuntimeException("全局故障注入: " + stepName);
        }
        AtomicInteger counter = faultMap.get(stepName);
        if (counter != null && counter.get() > 0) {
            counter.decrementAndGet();
            throw new RuntimeException("注入故障: " + stepName + " (剩余 " + counter.get() + " 次)");
        }
    }

    public boolean isAnyFaultActive() {
        return globalEnabled || faultMap.values().stream().anyMatch(c -> c.get() > 0);
    }

    public java.util.Map<String, Integer> getStatus() {
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        faultMap.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
}

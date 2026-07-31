package com.minimax.dualrecord.saga;

/**
 * Saga 步骤接口
 *
 * doAction()    : 正向执行
 * compensate()  : 反向补偿
 */
public interface SagaStep {

    String getName();

    void doAction();

    void compensate();

    /**
     * 默认实现：调用 doAction + 记录补偿动作
     */
    static SagaStep of(String name, Runnable doAction, Runnable compensate) {
        return new SagaStep() {
            @Override public String getName() { return name; }
            @Override public void doAction() { doAction.run(); }
            @Override public void compensate() { compensate.run(); }
        };
    }
}

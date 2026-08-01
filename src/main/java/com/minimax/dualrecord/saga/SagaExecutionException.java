package com.minimax.dualrecord.saga;

/**
 * Saga 执行异常 - 表示 Saga 失败并已触发补偿
 */
public class SagaExecutionException extends RuntimeException {
    public SagaExecutionException(String message) {
        super(message);
    }
    public SagaExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

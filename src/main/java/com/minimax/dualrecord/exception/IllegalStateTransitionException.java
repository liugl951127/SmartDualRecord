package com.minimax.dualrecord.exception;

/**
 * 非法状态转移异常
 */
public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(String message) {
        super(message);
    }
}

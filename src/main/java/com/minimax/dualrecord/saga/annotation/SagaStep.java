package com.minimax.dualrecord.saga.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Saga 步骤标注
 * 在 Saga 内部每个子事务方法上加此注解
 * 失败时自动调用 compensate() 指定的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaStep {

    /**
     * 步骤顺序 (0-based, 从小到大依次执行)
     */
    int order();

    /**
     * 步骤名称 (用于审计 + 调试)
     */
    String name() default "";

    /**
     * 补偿方法名 (同类中)
     * 失败时 Saga 编排器会自动调用, 用于回滚
     */
    String compensate() default "";

    /**
     * 是否可重试
     */
    boolean retryable() default true;

    /**
     * 单步超时 (ms)
     */
    long timeoutMs() default 30000;

    /**
     * 是否必需步骤 (失败才触发补偿, false 则允许跳过)
     */
    boolean required() default true;
}

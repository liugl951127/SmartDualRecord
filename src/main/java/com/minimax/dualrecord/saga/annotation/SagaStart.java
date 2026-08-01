package com.minimax.dualrecord.saga.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Saga 入口标注
 * 标注此注解的方法会触发 Saga 编排器, 自动执行后续 @SagaStep 步骤
 * 任何步骤失败会自动调用对应补偿方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaStart {

    /**
     * Saga 名称 (用于审计 + 查询)
     */
    String name() default "";

    /**
     * 业务 ID 在参数中的位置 (0-based) 或参数名
     * 留空时尝试从返回值取 businessId
     */
    String businessIdParam() default "";

    /**
     * 单步超时 (ms)
     */
    long stepTimeoutMs() default 30000;

    /**
     * 最大重试次数
     */
    int maxRetries() default 2;

    /**
     * 失败时是否自动补偿
     */
    boolean autoCompensate() default true;
}

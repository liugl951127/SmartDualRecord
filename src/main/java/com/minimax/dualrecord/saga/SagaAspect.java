package com.minimax.dualrecord.saga;

import com.minimax.dualrecord.saga.annotation.SagaStart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Saga AOP 切面
 * 拦截 @SagaStart 注解的方法, 自动编排步骤和补偿
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaAspect {

    private final SagaOrchestrator orchestrator;

    /**
     * 拦截 @SagaStart 方法
     * 实际不调用原方法, 由编排器根据 @SagaStep 注解按顺序执行
     */
    @Around("@annotation(com.minimax.dualrecord.saga.annotation.SagaStart)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        SagaStart anno = method.getAnnotation(SagaStart.class);

        log.info("🔄 Saga Aspect 拦截: {}.{} ({})",
            method.getDeclaringClass().getSimpleName(), method.getName(), anno.name());

        try {
            // 委托给编排器 (编排器会自己读 @SagaStep 注解, 不调用原方法)
            Object result = orchestrator.execute(pjp.getTarget(), method, pjp.getArgs());
            return result;
        } catch (SagaExecutionException e) {
            // Saga 失败, 已补偿, 向上抛
            throw e;
        }
    }
}

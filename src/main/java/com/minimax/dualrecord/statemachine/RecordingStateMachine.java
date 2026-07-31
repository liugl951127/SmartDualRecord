package com.minimax.dualrecord.statemachine;

import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.exception.IllegalStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 双录状态机 · 静态规则
 *
 * 状态转移表是显式枚举的（不是动态配置的），便于审查和测试。
 * 任何非法转移直接抛异常，由 @Transactional 事务回滚机制处理。
 *
 * 设计原则：
 *  1. 任何状态变更都必须查这张表
 *  2. 失败状态（FAILED）可由人工标 FAIL 终态
 *  3. 终态（ARCHIVED）只能向前
 */
public final class RecordingStateMachine {

    private static final Map<RecordingState, Set<RecordingState>> TRANSITIONS;

    static {
        TRANSITIONS = new EnumMap<>(RecordingState.class);
        TRANSITIONS.put(RecordingState.INIT,
                EnumSet.of(RecordingState.IDENTITY_VERIFIED, RecordingState.FAILED));
        TRANSITIONS.put(RecordingState.IDENTITY_VERIFIED,
                EnumSet.of(RecordingState.RISK_ASSESSED, RecordingState.FAILED));
        TRANSITIONS.put(RecordingState.RISK_ASSESSED,
                EnumSet.of(RecordingState.SCRIPT_LOADED, RecordingState.FAILED));
        TRANSITIONS.put(RecordingState.SCRIPT_LOADED,
                EnumSet.of(RecordingState.RECORDING, RecordingState.FAILED));
        TRANSITIONS.put(RecordingState.RECORDING,
                EnumSet.of(RecordingState.RECORDED, RecordingState.FAILED, RecordingState.ROLLED_BACK));
        TRANSITIONS.put(RecordingState.RECORDED,
                EnumSet.of(RecordingState.AI_QA));
        TRANSITIONS.put(RecordingState.AI_QA,
                EnumSet.of(RecordingState.AI_QA_PASSED, RecordingState.AI_QA_FLAGGED));
        TRANSITIONS.put(RecordingState.AI_QA_FLAGGED,
                EnumSet.of(RecordingState.HUMAN_REVIEW, RecordingState.ROLLED_BACK));
        TRANSITIONS.put(RecordingState.HUMAN_REVIEW,
                EnumSet.of(RecordingState.HUMAN_REVIEWED));
        TRANSITIONS.put(RecordingState.HUMAN_REVIEWED,
                EnumSet.of(RecordingState.SIGNED, RecordingState.ROLLED_BACK));
        TRANSITIONS.put(RecordingState.SIGNED,
                EnumSet.of(RecordingState.ARCHIVED));
        TRANSITIONS.put(RecordingState.FAILED,
                EnumSet.of(RecordingState.ROLLED_BACK,
                        RecordingState.INIT,
                        RecordingState.IDENTITY_VERIFIED,
                        RecordingState.RISK_ASSESSED,
                        RecordingState.SCRIPT_LOADED));
        TRANSITIONS.put(RecordingState.ROLLED_BACK, EnumSet.noneOf(RecordingState.class));
        TRANSITIONS.put(RecordingState.AI_QA_PASSED,
                EnumSet.of(RecordingState.SIGNED, RecordingState.HUMAN_REVIEW));
        TRANSITIONS.put(RecordingState.ARCHIVED, EnumSet.noneOf(RecordingState.class));
    }

    private RecordingStateMachine() {}

    /**
     * 判断 from → to 是否为合法转移
     */
    public static boolean canTransition(RecordingState from, RecordingState to) {
        if (from == null || to == null) return false;
        if (from == to) return true; // 自反
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RecordingState.class)).contains(to);
    }

    /**
     * 执行状态转移：合法则返回新状态；非法则抛异常
     */
    public static RecordingState transition(RecordingState from, RecordingState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(
                    String.format("非法状态转移: %s → %s（合法目标: %s）",
                            from, to, TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RecordingState.class))));
        }
        return to;
    }

    /**
     * 获取所有合法目标状态
     */
    public static Set<RecordingState> nextStates(RecordingState from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RecordingState.class));
    }
}

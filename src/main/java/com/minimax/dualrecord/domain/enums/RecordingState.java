package com.minimax.dualrecord.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 双录状态机 · 全部状态
 *
 * 状态转移规则：
 *   INIT → IDENTITY_VERIFIED → RISK_ASSESSED → SCRIPT_LOADED →
 *   RECORDING → RECORDED → AI_QA → AI_QA_PASSED|AI_QA_FLAGGED →
 *   HUMAN_REVIEW → HUMAN_REVIEWED → SIGNED → ARCHIVED
 *
 * 任何中断会回滚到上一个事务提交点。
 */
public enum RecordingState {
    /** 客户进入双录 */
    INIT,
    /** 身份核验通过 */
    IDENTITY_VERIFIED,
    /** 风险评估完成 */
    RISK_ASSESSED,
    /** 话术加载完成 */
    SCRIPT_LOADED,
    /** 录像中 */
    RECORDING,
    /** 录像完成 */
    RECORDED,
    /** AI 质检中 */
    AI_QA,
    /** AI 质检通过 */
    AI_QA_PASSED,
    /** AI 标红待人工 */
    AI_QA_FLAGGED,
    /** 人工复核中 */
    HUMAN_REVIEW,
    /** 人工复核完成 */
    HUMAN_REVIEWED,
    /** 客户签字 */
    SIGNED,
    /** 归档 */
    ARCHIVED,
    /** 失败（可恢复） */
    FAILED,
    /** 线下双录某节点未通过, 等待线上补录 (v1.5 跨渠道补录) */
    OFFLINE_FAILED,
    /** 已回滚（一致性恢复） */
    ROLLED_BACK;

    private static final Set<RecordingState> TERMINAL = EnumSet.of(ARCHIVED, ROLLED_BACK);
    private static final Set<RecordingState> FAILED_STATES = EnumSet.of(FAILED, OFFLINE_FAILED, ROLLED_BACK);
    private static final Set<RecordingState> RESUMABLE = EnumSet.of(OFFLINE_FAILED, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isFailed() {
        return FAILED_STATES.contains(this);
    }

    /** 是否可恢复 (允许从失败节点继续) */
    public boolean isResumable() {
        return RESUMABLE.contains(this);
    }
}

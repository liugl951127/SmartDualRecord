package com.minimax.dualrecord.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 反深伪检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionResult {
    /** 综合判定：NORMAL / SUSPECTED / CONFIRMED */
    private String verdict;
    /** 综合分数（0-1，> 0.92 触发兜底） */
    private Double overallScore;
    /** 4 路分项打分 */
    private Map<String, Double> channelScores;
    /** 是否需要人工接管 */
    private Boolean requireHumanTakeover;
    /** 命中证据描述 */
    private String evidence;
    /** 模型版本 */
    private String modelVersion;
    /** 4 路并行推理总耗时 (ms) */
    private Long latencyMs;
}

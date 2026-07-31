package com.minimax.dualrecord.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ASR 转写结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrResult {
    /** 完整转写文本 */
    private String fullText;
    /** 句子级带时间戳的转写（用于证据切片） */
    private List<Segment> segments;
    /** 是否识别到关键肯定词（"是的"/"清楚"/"明白"等） */
    private Boolean containsAffirmative;
    /** 识别置信度 */
    private Double confidence;
    /** 说话人（0=坐席，1=客户） */
    private List<Integer> speakerIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private String text;
        private Integer startMs;
        private Integer endMs;
        private Integer speakerId;
    }
}

package com.minimax.dualrecord.ai.impl;

import com.minimax.dualrecord.ai.AsrResult;
import com.minimax.dualrecord.ai.AsrService;
import com.minimax.dualrecord.ai.StreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock ASR 服务 · 沙箱模式使用
 * 沙箱里直接返回固定的中文转写，模拟"客户说"和"坐席说"
 */
@Slf4j
@Component
public class MockAsrService implements AsrService {

    @Override
    public AsrResult transcribe(byte[] audioBytes) {
        log.debug("Mock ASR 转写: audioBytes={}", audioBytes == null ? 0 : audioBytes.length);
        List<AsrResult.Segment> segments = new ArrayList<>();
        segments.add(AsrResult.Segment.builder()
                .text("您好，欢迎来到理财专区。")
                .startMs(0).endMs(2500).speakerId(0).build());
        segments.add(AsrResult.Segment.builder()
                .text("我想咨询一下稳健型理财产品。")
                .startMs(3000).endMs(5500).speakerId(1).build());
        segments.add(AsrResult.Segment.builder()
                .text("好的，本产品为非保本浮动收益型理财，请问您是否已了解？")
                .startMs(6000).endMs(9500).speakerId(0).build());
        segments.add(AsrResult.Segment.builder()
                .text("是的，我清楚了。")
                .startMs(10000).endMs(12000).speakerId(1).build());

        boolean containsAff = segments.stream()
                .anyMatch(s -> s.getText().contains("是的") || s.getText().contains("清楚") || s.getText().contains("明白"));

        return AsrResult.builder()
                .fullText(String.join("", segments.stream().map(AsrResult.Segment::getText).toList()))
                .segments(segments)
                .containsAffirmative(containsAff)
                .confidence(0.94)
                .speakerIds(List.of(0, 1))
                .build();
    }

    @Override
    public void streamTranscribe(String businessId, byte[] chunk, StreamHandler handler) {
        handler.onChunk("的客户您好。", false);
    }
}

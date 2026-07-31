package com.minimax.dualrecord.service;

import com.minimax.dualrecord.ai.LlmGateway;
import com.minimax.dualrecord.ai.LlmRequest;
import com.minimax.dualrecord.ai.LlmResponse;
import com.minimax.dualrecord.domain.QaResult;
import com.minimax.dualrecord.repository.QaResultRepository;
import com.minimax.dualrecord.util.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 质检服务
 *
 * 流程：
 *  1. 100% AI 预筛（音视频转写 + 关键节点关键词 + 禁播词）
 *  2. 高风险产品 100% 人工复核
 *  3. 普通产品 30% 抽检
 *  4. 标红 PASS / PASS_WITH_FINDINGS / FAIL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final LlmGateway llmGateway;
    private final ComplianceService complianceService;
    private final QaResultRepository repository;
    private final BusinessIdGenerator idGenerator;

    /**
     * AI 预筛
     */
    public QaResult aiPreScreen(String businessId, String recId, String fullAsrText) {
        log.info("AI 预筛: businessId={}, recId={}", businessId, recId);

        // 1. 禁播词扫描
        List<ComplianceService.Hit> hits = complianceService.scan(fullAsrText);
        boolean hasHighSeverity = hits.stream().anyMatch(h -> "HIGH".equals(h.severity()));

        // 2. LLM 综合评估
        LlmRequest llmReq = LlmRequest.builder()
                .businessId(businessId)
                .systemPrompt("你是双录质检员，请基于 ASR 转写和禁播词命中情况给出综合评分。")
                .userPrompt(String.format("""
                        请评估以下双录内容并给出 JSON 评分：
                        ASR 转写：%s
                        禁播词命中：%s
                        """, fullAsrText, hits))
                .jsonMode(true)
                .build();
        LlmResponse llmResp = llmGateway.complete(llmReq);

        // 3. 解析评分
        BigDecimal score = extractScore(llmResp.getContent());
        String result = determineResult(score, hasHighSeverity);

        // 4. 落库
        QaResult qa = new QaResult();
        qa.setQaId(idGenerator.generateQaId());
        qa.setRecId(recId);
        qa.setBusinessId(businessId);
        qa.setCheckerType("AI");
        qa.setAiModelVersion(llmResp.getModelVersion());
        qa.setAiQaScore(score);
        qa.setAiQaResult(result);
        qa.setIssuesJson(toJson(hits));
        qa.setCheckTime(LocalDateTime.now());
        qa.setDeleted(0);
        repository.insert(qa);

        log.info("AI 预筛完成: qaId={}, score={}, result={}", qa.getQaId(), score, result);
        return qa;
    }

    private BigDecimal extractScore(String llmContent) {
        // 简化的 JSON 解析
        try {
            if (llmContent.contains("\"score\"")) {
                int start = llmContent.indexOf("\"score\"") + 8;
                int end = llmContent.indexOf(",", start);
                if (end == -1) end = llmContent.indexOf("}", start);
                String val = llmContent.substring(start, end).replaceAll("[^0-9.]", "");
                return new BigDecimal(val);
            }
        } catch (Exception ignored) {}
        return BigDecimal.valueOf(85.0);  // 默认 85 分
    }

    private String determineResult(BigDecimal score, boolean hasHighSeverity) {
        if (hasHighSeverity) return "FAIL";
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return "PASS";
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) return "PASS_WITH_FINDINGS";
        return "FAIL";
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}

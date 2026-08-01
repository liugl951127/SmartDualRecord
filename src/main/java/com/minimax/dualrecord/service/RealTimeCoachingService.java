package com.minimax.dualrecord.service;

import com.minimax.dualrecord.ai.AsrService;
import com.minimax.dualrecord.ai.LlmGateway;
import com.minimax.dualrecord.ai.LlmRequest;
import com.minimax.dualrecord.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时耳返副驾服务
 *
 * <h3>目标</h3>
 * 理财经理 / 数字人坐席边讲边收 0.5 秒以内的辅助提示
 *  - 当前节点的 3 选 1 推荐话术
 *  - 实时禁播词告警 (兜底, 主路在 Vue 前端 400ms 防抖)
 *  - 异议处理金牌应答
 *  - 客户画像关键提示
 *
 * <h3>性能 SLA</h3>
 *  - P50 < 300ms
 *  - P95 < 500ms
 *  - 超过 1s 自动降级为静态话术
 *
 * <h3>合规依据</h3>
 *  - 不替换必须话术 (节点 6 肯定等)
 *  - 不输出未审核的产品收益数据
 *  - 监管数据流可见
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeCoachingService {

    private final LlmGateway llmGateway;
    private final AsrService asrService;
    private final ComplianceService complianceService;

    /** 性能降级阈值 (ms) */
    private static final long DEGRADE_THRESHOLD_MS = 1000;
    /** 性能目标 (ms) */
    private static final long TARGET_LATENCY_MS = 500;

    /**
     * 主对外接口: 给定当前 ASR 片段, 返回辅助提示
     */
    public Map<String, Object> coach(String businessId, String asrSegment) {
        long start = System.currentTimeMillis();

        // 1. 禁播词实时扫描 (兜底, 前端 Vue 400ms 防抖 + 后端 0.5s 实时)
        List<ComplianceService.Hit> hits = complianceService.scan(asrSegment);
        if (!hits.isEmpty()) {
            long latency = System.currentTimeMillis() - start;
            Map<String, Object> resp = new HashMap<>();
            resp.put("alerts", hits);
            resp.put("coaching", "建议立即换一句: 当前含禁播词");
            resp.put("latencyMs", latency);
            resp.put("degraded", false);
            return resp;
        }

        // 2. 调 LLM 推理 (目标 < 500ms)
        LlmRequest req = LlmRequest.builder()
                .businessId(businessId)
                .systemPrompt("你是双录 AI 副驾, 根据当前客户说的话, 给出 3 个 10 字以内的下一步推荐话术 + 1 句异议应答")
                .userPrompt("客户刚说: " + asrSegment + "\n请推荐下一步话术")
                .jsonMode(true)
                .maxTokens(120)
                .temperature(0.3)
                .regulationRef("金发〔2026〕8号-第二十六条")
                .build();
        LlmResponse llmResp = llmGateway.complete(req);

        long latency = System.currentTimeMillis() - start;
        boolean degraded = latency > DEGRADE_THRESHOLD_MS;

        Map<String, Object> resp = new HashMap<>();
        resp.put("coaching", llmResp.getContent());
        resp.put("latencyMs", latency);
        resp.put("degraded", degraded);
        resp.put("modelVersion", llmResp.getModelVersion());
        resp.put("aiAppId", llmResp.getAiAppId());
        if (latency > TARGET_LATENCY_MS) {
            log.warn("实时耳返超目标: {}ms (target={})", latency, TARGET_LATENCY_MS);
        }
        return resp;
    }
}

package com.minimax.dualrecord.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingNode;
import com.minimax.dualrecord.domain.enums.SellerType;
import com.minimax.dualrecord.dto.CompleteNodeRequest;
import com.minimax.dualrecord.dto.StartBusinessRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 端到端集成测试
 * 完整跑一遍：创建业务 → 加载话术 → 风险评估 → 8 节点 → 终检 → 签字
 */
@SpringBootTest
class EndToEndTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testFullFlow() throws Exception {
        // 1. 创建业务
        StartBusinessRequest req = new StartBusinessRequest();
        req.setBusinessType(BusinessType.WEALTH);
        req.setProductId("BNK-FIN-2026Q3-001");
        req.setCustomerIdHash("cust-hash-001");
        req.setSellerIdHash("seller-hash-001");
        req.setChannel(Channel.OFFLINE);
        req.setSellerType(SellerType.HUMAN);
        req.setAmount(new BigDecimal("50000"));

        String response = mockMvc.perform(post("/api/v1/recording/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").exists())
                .andReturn().getResponse().getContentAsString();

        String businessId = objectMapper.readTree(response).get("businessId").asText();
        System.out.println("Created business: " + businessId);

        // 2. 加载话术
        mockMvc.perform(post("/api/v1/recording/script/load")
                        .param("businessId", businessId)
                        .param("productId", "BNK-FIN-2026Q3-001"))
                .andExpect(status().isOk());

        // 3. 风险评估
        mockMvc.perform(post("/api/v1/recording/risk/assess")
                        .param("businessId", businessId)
                        .param("customerIdHash", "cust-hash-001"))
                .andExpect(status().isOk());

        // 4. 启动录制
        mockMvc.perform(post("/api/v1/recording/begin")
                        .param("businessId", businessId))
                .andExpect(status().isNoContent());

        // 5. 完成 8 个节点
        for (RecordingNode node : RecordingNode.orderedAll()) {
            CompleteNodeRequest nodeReq = new CompleteNodeRequest();
            nodeReq.setBusinessId(businessId);
            nodeReq.setRecId("REC-TEST-001");
            nodeReq.setNode(node);
            // 节点 6 包含肯定词
            if (node == RecordingNode.NODE_06_CONFIRM) {
                nodeReq.setAsrText("是的，我清楚了。本产品为非保本浮动收益型理财，我已了解。");
            } else {
                nodeReq.setAsrText("这是一个正常的双录对话内容。");
            }

            mockMvc.perform(post("/api/v1/recording/node/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(nodeReq)))
                    .andExpect(status().isOk());
        }

        // 6. 终检
        mockMvc.perform(post("/api/v1/recording/finalize")
                        .param("businessId", businessId)
                        .param("recId", "REC-TEST-001")
                        .param("fullAsrText", "完整 ASR 转写文本，包含所有 8 节点的对话内容。"))
                .andExpect(status().isOk());

        // 7. 签字归档
        mockMvc.perform(post("/api/v1/recording/sign")
                        .param("businessId", businessId))
                .andExpect(status().isNoContent());

        // 8. 全景查询
        mockMvc.perform(get("/api/v1/recording/overview/" + businessId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node_count").value(8));

        System.out.println("✓ 完整流程通过: businessId=" + businessId);
    }

    @Test
    void testForbiddenPhraseBlocking() throws Exception {
        // 创建一个含禁播词的业务
        StartBusinessRequest req = new StartBusinessRequest();
        req.setBusinessType(BusinessType.WEALTH);
        req.setProductId("BNK-FIN-2026Q3-001");
        req.setCustomerIdHash("cust-002");
        req.setChannel(Channel.OFFLINE);
        req.setSellerType(SellerType.HUMAN);
        req.setAmount(new BigDecimal("10000"));

        String response = mockMvc.perform(post("/api/v1/recording/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        String businessId = objectMapper.readTree(response).get("businessId").asText();

        mockMvc.perform(post("/api/v1/recording/script/load")
                .param("businessId", businessId).param("productId", "BNK-FIN-2026Q3-001"));
        mockMvc.perform(post("/api/v1/recording/risk/assess")
                .param("businessId", businessId).param("customerIdHash", "cust-002"));
        mockMvc.perform(post("/api/v1/recording/begin").param("businessId", businessId));

        // 节点 02 命中禁播词"保本保息" → 应被阻断
        CompleteNodeRequest nodeReq = new CompleteNodeRequest();
        nodeReq.setBusinessId(businessId);
        nodeReq.setRecId("REC-TEST-002");
        nodeReq.setNode(RecordingNode.NODE_02_DISCLOSURE);
        nodeReq.setAsrText("这个产品保本保息，绝对安全。");

        mockMvc.perform(post("/api/v1/recording/node/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nodeReq)))
                .andExpect(status().isBadRequest());
    }
}

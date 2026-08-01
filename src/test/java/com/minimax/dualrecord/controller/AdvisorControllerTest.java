package com.minimax.dualrecord.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdvisorController 集成测试 (v1.5 H5 → PC 转接)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
class AdvisorControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;

    @Test
    void h5ToPcFlow() throws Exception {
        // 1. H5 查看可选理财经理
        mockMvc.perform(get("/api/v1/advisor/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].advisorId").exists())
                .andExpect(jsonPath("$[0].name").exists());

        // 2. H5 客户请求转接
        Map<String, Object> req = Map.of(
                "businessId", "BNK20260801-900001",
                "customerName", "张三",
                "customerMobile", "138****8000",
                "reason", "PRODUCT_QUESTION",
                "description", "对浮动收益有疑问, 需要人工解释"
        );
        String resp = mockMvc.perform(post("/api/v1/advisor/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn().getResponse().getContentAsString();
        String sessionId = om.readTree(resp).get("sessionId").asText();
        System.out.println("✓ H5 客户请求转接: sessionId=" + sessionId);

        // 3. PC 查看待处理
        mockMvc.perform(get("/api/v1/advisor/pending/teller-wang-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(sessionId));

        // 4. PC 接单
        mockMvc.perform(post("/api/v1/advisor/" + sessionId + "/accept")
                        .param("advisorId", "teller-wang-001")
                        .param("advisorName", "王经理"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        System.out.println("✓ PC 理财经理接单");

        // 5. 客户查活跃会话
        mockMvc.perform(get("/api/v1/advisor/active/BNK20260801-900001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advisorName").value("王经理"));

        // 6. 结束会话
        mockMvc.perform(post("/api/v1/advisor/" + sessionId + "/end")
                        .param("endReason", "BUSINESS_DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        System.out.println("✓ 会话结束");
    }

    @Test
    void declineFlow() throws Exception {
        Map<String, Object> req = Map.of(
                "businessId", "BNK20260801-900003",
                "customerName", "李四",
                "reason", "TECH_ISSUE"
        );
        String resp = mockMvc.perform(post("/api/v1/advisor/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sessionId = om.readTree(resp).get("sessionId").asText();

        // 拒绝
        mockMvc.perform(post("/api/v1/advisor/" + sessionId + "/decline")
                        .param("reason", "正在通话中"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }
}

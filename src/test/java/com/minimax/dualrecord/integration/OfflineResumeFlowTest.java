package com.minimax.dualrecord.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * v1.5 跨渠道补录流程集成测试
 *
 * 场景: 线下双录 → 节点失败 → 生成 token → 客户用 token 线上补录
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
class OfflineResumeFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    @Test
    void offlineFailedThenOnlineResume() throws Exception {
        // 1. 标记线下双录失败 (节点 02 风评通过 + 风评不匹配)
        String resp = mockMvc.perform(post("/api/v1/recording/offline-failed")
                        .param("businessId", "BNK20260801-900003")
                        .param("failedNode", "NODE_02_DISCLOSURE")
                        .param("reason", "NO_AFFIRMATIVE")
                        .param("detail", "客户未明确回答'是'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeToken").exists())
                .andExpect(jsonPath("$.resumeUrl").exists())
                .andReturn().getResponse().getContentAsString();
        Map<String, String> body = om.readValue(resp, Map.class);
        String token = body.get("resumeToken");
        System.out.println("✓ 线下失败 → 生成 token: " + token);

        // 2. 客户扫码调用 getResumeInfo
        mockMvc.perform(get("/api/v1/recording/resume-info/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedAtNode").value("NODE_02_DISCLOSURE"))
                .andExpect(jsonPath("$.reason").value("NO_AFFIRMATIVE"))
                .andExpect(jsonPath("$.resumeFromNodeOrder").value(2));

        // 3. 完成线上补录
        mockMvc.perform(post("/api/v1/recording/resume-complete")
                        .param("businessId", "BNK20260801-900003")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESUMED"));
        System.out.println("✓ 线上补录完成");

        // 4. 同一 token 重复查询应失败 (token 已清除)
        mockMvc.perform(get("/api/v1/recording/resume-info/" + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void invalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/recording/resume-info/invalid-token-xyz"))
                .andExpect(status().is4xxClientError());
    }
}

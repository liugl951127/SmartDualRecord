package com.minimax.dualrecord.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ScriptConfigController DB CRUD 集成测试 (v1.4)
 *
 * 覆盖:
 *  - 创建/更新产品话术 (POST /db-template)
 *  - 查询所有/单个 (GET /db-templates, /db-template/{id})
 *  - 状态机: 提交审核 → 批准 → 冻结
 *  - 删除 (DELETE /db-template/{id})
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
class ScriptConfigDbCrudTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    @Test
    void fullCrudFlow() throws Exception {
        // 1. 创建
        Map<String, Object> body = Map.of(
                "productId", "TEST-PRODUCT-001",
                "productType", "WEALTH",
                "version", "v1.0",
                "riskLevel", "R2",
                "mandatoryDisclosure", List.of("本产品为非保本浮动收益型理财"),
                "forbiddenPhrases", List.of("保证收益", "保本保息"),
                "requiredQuestions", List.of("您是否已了解风险?"),
                "channelOverrides", Map.of(
                        "OFFLINE", Map.of("syncMode", "same_frame", "watermarkVisible", false, "aiDisclosure", false, "audioIdPerMinute", 0),
                        "SELF_AI", Map.of("syncMode", "ai_with_disclosure", "watermarkVisible", true, "aiDisclosure", true, "audioIdPerMinute", 1)
                ),
                "status", "DRAFT"
        );
        String created = mockMvc.perform(post("/api/v1/script-config/db-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("TEST-PRODUCT-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String id = om.readTree(created).get("id").asText();

        // 2. 列表
        mockMvc.perform(get("/api/v1/script-config/db-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").exists());

        // 3. 单查
        mockMvc.perform(get("/api/v1/script-config/db-template/TEST-PRODUCT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forbiddenPhrases").isArray())
                .andExpect(jsonPath("$.forbiddenPhrases[0]").value("保证收益"));

        // 4. 提交审核
        String submitted = mockMvc.perform(post("/api/v1/script-config/db-template/" + id + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        System.out.println("✓ submit: " + submitted);

        // 5. 批准
        String approved = mockMvc.perform(post("/api/v1/script-config/db-template/" + id + "/approve")
                        .param("approver", "test-compliance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("test-compliance"))
                .andReturn().getResponse().getContentAsString();
        System.out.println("✓ approve: " + approved);

        // 6. 冻结
        String frozen = mockMvc.perform(post("/api/v1/script-config/db-template/" + id + "/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andReturn().getResponse().getContentAsString();
        System.out.println("✓ freeze: " + frozen);

        // 7. 删除 (清理)
        mockMvc.perform(delete("/api/v1/script-config/db-template/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
    }
}

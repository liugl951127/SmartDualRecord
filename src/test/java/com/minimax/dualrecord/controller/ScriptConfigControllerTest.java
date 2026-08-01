package com.minimax.dualrecord.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ScriptConfigController /api/v1/script-config/global 集成测试
 *
 * 验证修复: 之前直接返回 ScriptProperties CGLIB 代理 Bean
 *          Jackson 序列化时找不到 StandardBeanExpressionResolver 的 serializer
 *          修复后返回 Map<String,Object> 显式拷贝字段
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
class ScriptConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void globalConfigShouldReturnJsonMap() throws Exception {
        mockMvc.perform(get("/api/v1/script-config/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultRiskLevel").exists())
                .andExpect(jsonPath("$.defaultForbiddenPhrases").isArray())
                .andExpect(jsonPath("$.defaultMandatoryPhrases").isArray());
    }
}

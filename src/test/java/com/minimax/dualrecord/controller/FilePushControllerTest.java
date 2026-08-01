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
 * FilePushController 集成测试 (v1.5)
 *
 * 流程: 坐席推送 → 客户查看 → 客户签署
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
class FilePushControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;

    @Test
    void fullFilePushFlow() throws Exception {
        // 1. 坐席推送文件
        Map<String, Object> pushReq = Map.of(
                "businessId", "BNK20260801-900001",
                "fileName", "产品说明书.pdf",
                "fileType", "PDF",
                "fileCategory", "BROCHURE",
                "fileUrl", "/static/templates/product-brochure.pdf",
                "fileSize", 1024 * 1024 * 2L,
                "remark", "请客户查看本产品的详细信息"
        );
        String resp = mockMvc.perform(post("/api/v1/file/push")
                        .param("operatorId", "teller-wang")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(pushReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").exists())
                .andExpect(jsonPath("$.status").value("PUSHED"))
                .andReturn().getResponse().getContentAsString();
        String fileId = om.readTree(resp).get("fileId").asText();
        System.out.println("✓ 推送文件: fileId=" + fileId);

        // 2. 列表查询
        mockMvc.perform(get("/api/v1/file/list/BNK20260801-900001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileId").value(fileId));

        // 3. 客户查看
        mockMvc.perform(post("/api/v1/file/" + fileId + "/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VIEWED"))
                .andExpect(jsonPath("$.viewedAt").exists());
        System.out.println("✓ 客户已查看");

        // 4. 客户签署
        Map<String, Object> signReq = Map.of(
                "signatureData", "data:image/png;base64,iVBORw0KGgo...",
                "rejected", false
        );
        mockMvc.perform(post("/api/v1/file/" + fileId + "/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(signReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedAt").exists())
                .andExpect(jsonPath("$.signatureData").exists());
        System.out.println("✓ 客户已签署");
    }

    @Test
    void rejectFlow() throws Exception {
        // 推送
        Map<String, Object> pushReq = Map.of(
                "businessId", "BNK20260801-900001",
                "fileName", "附加合同.pdf",
                "fileType", "PDF",
                "fileCategory", "CONTRACT",
                "fileUrl", "/x.pdf",
                "fileSize", 1024L
        );
        String resp = mockMvc.perform(post("/api/v1/file/push")
                        .param("operatorId", "teller-wang")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(pushReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = om.readTree(resp).get("fileId").asText();

        // 拒签
        Map<String, Object> signReq = Map.of(
                "rejected", true,
                "rejectReason", "对收益条款有异议"
        );
        mockMvc.perform(post("/api/v1/file/" + fileId + "/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(signReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void templatesList() throws Exception {
        mockMvc.perform(get("/api/v1/file/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].category").exists());
    }
}

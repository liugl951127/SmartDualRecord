package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.*;
import com.minimax.dualrecord.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据血缘服务 - 跨表关联追溯
 *
 * 业务 -> 录像 -> 节点 -> 风险评估 -> 话术 -> 禁播词 -> 事件 -> 签字
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataLineageService {

    private final DataLineageRepository lineageRepository;
    private final BusinessRepository businessRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingNodeRepository nodeRepository;
    private final RiskAssessmentRepository riskRepository;
    private final QaResultRepository qaRepository;
    private final EventLogRepository eventRepository;
    private final ScriptTemplateRepository scriptRepository;
    private final AdvisorSessionRepository advisorRepository;
    private final AuditChainService auditChainService;

    /**
     * 记录一条血缘关系
     */
    public void link(String businessId, String parentType, String parentId,
                     String childType, String childId,
                     String relationType, String meta) {
        DataLineage l = new DataLineage();
        l.setId("lin-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        l.setBusinessId(businessId);
        l.setParentType(parentType);
        l.setParentId(parentId);
        l.setChildType(childType);
        l.setChildId(childId);
        l.setRelationType(relationType);
        l.setRelationMeta(meta);
        l.setCreatedAt(LocalDateTime.now());
        lineageRepository.insert(l);
    }

    /**
     * 业务全链路血缘图
     */
    public Map<String, Object> getBusinessLineage(String businessId) {
        long t = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessId", businessId);

        // 1. 业务主体
        Business business = businessRepository.selectOne(new QueryWrapper<Business>()
            .eq("business_id", businessId));
        if (business == null) {
            result.put("error", "业务不存在: " + businessId);
            return result;
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 业务节点
        Map<String, Object> bizNode = new LinkedHashMap<>();
        bizNode.put("id", "B-" + business.getId());
        bizNode.put("type", "business");
        bizNode.put("label", "业务 " + business.getBusinessId());
        bizNode.put("state", business.getState());
        bizNode.put("amount", business.getAmount());
        bizNode.put("risk", business.getRiskLevel());
        nodes.add(bizNode);

        // 2. 录像
        List<Recording> recordings = recordingRepository.selectList(new QueryWrapper<Recording>()
            .eq("business_id", businessId));
        for (Recording r : recordings) {
            nodes.add(mapOf(
                "id", "R-" + r.getId(),
                "type", "recording",
                "label", "录像 " + r.getRecId(),
                "channel", r.getChannel(),
                "durationMs", r.getDurationMs(),
                "encryption", r.getEncryption()
            ));
            edges.add(mapOf(
                "from", "B-" + business.getId(),
                "to", "R-" + r.getId(),
                "relation", "HAS_RECORDING"
            ));

            // 3. 节点明细
            List<RecordingNodeDetail> recNodes = nodeRepository.selectList(new QueryWrapper<RecordingNodeDetail>()
                .eq("rec_id", r.getRecId()));
            for (RecordingNodeDetail n : recNodes) {
                nodes.add(mapOf(
                    "id", "N-" + n.getId(),
                    "type", "node",
                    "label", n.getNodeId(),
                    "completed", n.getCompleted()
                ));
                edges.add(mapOf(
                    "from", "R-" + r.getId(),
                    "to", "N-" + n.getId(),
                    "relation", "CONTAINS_NODE"
                ));
            }
        }

        // 4. 风险评估
        List<RiskAssessment> risks = riskRepository.selectList(new QueryWrapper<RiskAssessment>()
            .eq("customer_id_hash", business.getCustomerIdHash())
            .orderByDesc("assessed_at")
            .last("LIMIT 3"));
        for (RiskAssessment risk : risks) {
            nodes.add(mapOf(
                "id", "K-" + risk.getId(),
                "type", "risk",
                "label", "风评 " + risk.getRiskLevel(),
                "score", risk.getOverallScore(),
                "assessedAt", risk.getAssessedAt()
            ));
            edges.add(mapOf(
                "from", "B-" + business.getId(),
                "to", "K-" + risk.getId(),
                "relation", "USES_RISK"
            ));
        }

        // 5. AI 终检
        List<QaResult> qas = qaRepository.selectList(new QueryWrapper<QaResult>()
            .eq("business_id", businessId));
        for (QaResult qa : qas) {
            nodes.add(mapOf(
                "id", "Q-" + qa.getId(),
                "type", "qa",
                "label", "AI 终检 " + (qa.getAiQaResult() != null ? qa.getAiQaResult() : "PENDING"),
                "score", qa.getAiQaScore()
            ));
            edges.add(mapOf(
                "from", "B-" + business.getId(),
                "to", "Q-" + qa.getId(),
                "relation", "AI_QA_RESULT"
            ));
        }

        // 6. 话术
        if (business.getProductId() != null) {
            ScriptTemplate script = scriptRepository.selectOne(new QueryWrapper<ScriptTemplate>()
                .eq("product_id", business.getProductId()));
            if (script != null) {
                nodes.add(mapOf(
                    "id", "S-" + script.getId(),
                    "type", "script",
                    "label", "话术 " + script.getProductId(),
                    "risk", script.getRiskLevel()
                ));
                edges.add(mapOf(
                    "from", "B-" + business.getId(),
                    "to", "S-" + script.getId(),
                    "relation", "USES_SCRIPT"
                ));
            }
        }

        // 7. 事件
        List<EventLog> events = eventRepository.selectList(new QueryWrapper<EventLog>()
            .eq("business_id", businessId)
            .orderByAsc("created_at")
            .last("LIMIT 50"));
        for (EventLog e : events) {
            nodes.add(mapOf(
                "id", "E-" + e.getId(),
                "type", "event",
                "label", e.getEventType(),
                "fromState", e.getFromState(),
                "toState", e.getToState()
            ));
            edges.add(mapOf(
                "from", "B-" + business.getId(),
                "to", "E-" + e.getId(),
                "relation", "TRIGGERS"
            ));
        }

        // 8. 顾问会话
        List<AdvisorSession> advisors = advisorRepository.selectList(new QueryWrapper<AdvisorSession>()
            .eq("business_id", businessId));
        for (AdvisorSession a : advisors) {
            nodes.add(mapOf(
                "id", "A-" + a.getId(),
                "type", "advisor",
                "label", "顾问 " + (a.getAdvisorName() != null ? a.getAdvisorName() : a.getAdvisorId()),
                "reason", a.getReason(),
                "status", a.getStatus()
            ));
            edges.add(mapOf(
                "from", "B-" + business.getId(),
                "to", "A-" + a.getId(),
                "relation", "TRANSFERRED_TO"
            ));
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        result.put("queryMs", System.currentTimeMillis() - t);
        return result;
    }

    /**
     * 血缘统计 - 全网
     */
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBusinesses", businessRepository.selectCount(null));

        // 按 type 统计
        List<Map<String, Object>> typeStats = new ArrayList<>();
        String[] types = {"business", "recording", "node", "risk", "qa", "script", "event", "advisor"};
        // (简化: 只列总数)
        for (String t : types) {
            typeStats.add(mapOf("type", t, "label", t));
        }
        result.put("typeStats", typeStats);
        return result;
    }

    /**
     * 反向追溯 - 找出某 entity 被哪些业务使用
     */
    public List<String> findBusinessesUsing(String entityType, String entityId) {
        return lineageRepository.selectList(new QueryWrapper<DataLineage>()
            .eq("child_type", entityType)
            .eq("child_id", entityId)
            .select("DISTINCT business_id"))
            .stream()
            .map(DataLineage::getBusinessId)
            .distinct()
            .collect(Collectors.toList());
    }

    // ==================== Helper ====================
    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }
}

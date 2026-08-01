package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.AuditChainEntry;
import com.minimax.dualrecord.repository.AuditChainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 审计链服务 - 不可变 hash 链 + HMAC 签名
 *
 * 设计:
 *  1. 每个 mutation (CREATE/UPDATE/DELETE) 都会 append 一条 entry
 *  2. chain_hash = SHA-256(prev_hash + payload_hash + metadata)
 *  3. hmac_signature = HMAC-SHA256(server_secret, chain_hash)
 *  4. 任何对历史 entry 的修改都会导致后续 verify 失败
 *  5. 每 100 条聚合成 Merkle root (用于批量验签)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditChainService {

    private final AuditChainRepository auditChainRepository;

    @Value("${dual-record.audit-chain.secret:default-server-secret-CHANGE-IN-PROD-2026}")
    private String serverSecret;

    @Value("${dual-record.audit-chain.node-id:node-1}")
    private String serverNode;

    @Value("${dual-record.audit-chain.merkle-batch:100}")
    private int merkleBatchSize;

    // ============ Append Entry ============

    /**
     * 添加一条审计链记录
     * @return 新条目的 chain_hash
     */
    public String append(String chainId,
                         String operationType,
                         String entityType,
                         String entityId,
                         String businessId,
                         String actorId,
                         String actorRole,
                         Object payload) {
        // 1. 计算 payload 哈希
        String payloadJson = toJson(payload);
        String payloadHash = sha256(payloadJson);

        // 2. 查链上最后一条
        AuditChainEntry last = getLastEntry(chainId);
        long nextSeq = (last == null) ? 0L : (last.getSequenceNo() + 1);
        String prevHash = (last == null) ? "GENESIS" : last.getChainHash();

        // 3. 拼 chain content (不含时间戳, 时间戳只用于存储, 不参与 hash)
        String chainContent = String.join("|",
            chainId,
            String.valueOf(nextSeq),
            operationType,
            entityType,
            entityId,
            businessId == null ? "" : businessId,
            actorId == null ? "" : actorId,
            actorRole == null ? "" : actorRole,
            payloadHash,
            prevHash,
            serverNode
        );
        String chainHash = sha256(chainContent);

        // 4. HMAC 签名
        String hmac = hmacSha256(serverSecret, chainHash);

        // 5. 构造 entry
        AuditChainEntry entry = new AuditChainEntry();
        entry.setId("audit-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        entry.setChainId(chainId);
        entry.setSequenceNo(nextSeq);
        entry.setOperationType(operationType);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBusinessId(businessId);
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setPayloadHash(payloadHash);
        entry.setPayloadJson(truncate(payloadJson, 64000));  // TEXT 64K 限制
        entry.setPrevHash(prevHash);
        entry.setChainHash(chainHash);
        entry.setHmacSignature(hmac);
        entry.setServerNode(serverNode);
        entry.setSignedAt(LocalDateTime.now());

        // 6. 每 N 条计算 merkle root
        if (nextSeq > 0 && nextSeq % merkleBatchSize == 0) {
            String merkleRoot = computeMerkleRoot(chainId, nextSeq - merkleBatchSize + 1, nextSeq);
            // 更新这一批最后一条
            entry.setMerkleRoot(merkleRoot);
        }

        auditChainRepository.insert(entry);
        log.debug("AuditChain append: chain={} seq={} entity={}/{} hash={}",
            chainId, nextSeq, entityType, entityId, chainHash.substring(0, 8));
        return chainHash;
    }

    /**
     * 业务专链 - chain_id 规则: biz-{businessId}-{yyyyMMdd}
     */
    public String appendForBusiness(String businessId,
                                    String operationType,
                                    String entityType,
                                    String entityId,
                                    Object payload,
                                    String actorId,
                                    String actorRole) {
        String chainId = "biz-" + businessId;
        return append(chainId, operationType, entityType, entityId, businessId, actorId, actorRole, payload);
    }

    /**
     * 全局链 - 系统级操作
     */
    public String appendGlobal(String operationType,
                               String entityType,
                               String entityId,
                               Object payload,
                               String actorId,
                               String actorRole) {
        String chainId = "global-" + LocalDateTime.now().toString().substring(0, 10);
        return append(chainId, operationType, entityType, entityId, null, actorId, actorRole, payload);
    }

    // ============ Verify ============

    /**
     * 验证整条链
     * @return 验证结果
     */
    public Map<String, Object> verifyChain(String chainId) {
        long start = System.currentTimeMillis();
        List<AuditChainEntry> entries = listChain(chainId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chainId", chainId);
        result.put("totalEntries", entries.size());
        result.put("checkType", "CHAIN_VERIFY");
        result.put("startedAt", LocalDateTime.now());

        if (entries.isEmpty()) {
            result.put("status", "PASSED");
            result.put("message", "链为空, 无需验证");
            result.put("finishedAt", LocalDateTime.now());
            result.put("durationMs", System.currentTimeMillis() - start);
            return result;
        }

        int passed = 0;
        int failed = 0;
        List<Map<String, Object>> brokenLinks = new ArrayList<>();
        String expectedPrevHash = "GENESIS";

        for (AuditChainEntry entry : entries) {
            // 1. 验证 prev_hash 链
            if (!Objects.equals(entry.getPrevHash(), expectedPrevHash)) {
                failed++;
                brokenLinks.add(Map.of(
                    "sequenceNo", entry.getSequenceNo(),
                    "expected", expectedPrevHash.substring(0, Math.min(12, expectedPrevHash.length())),
                    "actual", entry.getPrevHash().substring(0, Math.min(12, entry.getPrevHash().length())),
                    "reason", "PREV_HASH_MISMATCH"
                ));
                // 不立即 break, 继续检查后续 (防止 single-point 错误传播)
            } else {
                // 2. 验证 chain_hash
                String chainContent = String.join("|",
                    entry.getChainId(),
                    String.valueOf(entry.getSequenceNo()),
                    entry.getOperationType(),
                    entry.getEntityType(),
                    entry.getEntityId(),
                    entry.getBusinessId() == null ? "" : entry.getBusinessId(),
                    entry.getActorId() == null ? "" : entry.getActorId(),
                    entry.getActorRole() == null ? "" : entry.getActorRole(),
                    entry.getPayloadHash(),
                    entry.getPrevHash(),
                    entry.getServerNode()
                );
                String recomputed = sha256(chainContent);

                if (!Objects.equals(recomputed, entry.getChainHash())) {
                    failed++;
                    brokenLinks.add(Map.of(
                        "sequenceNo", entry.getSequenceNo(),
                        "expected", recomputed.substring(0, 12),
                        "actual", entry.getChainHash().substring(0, 12),
                        "reason", "CHAIN_HASH_TAMPERED"
                    ));
                } else {
                    // 3. 验证 HMAC
                    String recomputedHmac = hmacSha256(serverSecret, entry.getChainHash());
                    if (!Objects.equals(recomputedHmac, entry.getHmacSignature())) {
                        failed++;
                        brokenLinks.add(Map.of(
                            "sequenceNo", entry.getSequenceNo(),
                            "reason", "HMAC_INVALID"
                        ));
                    } else {
                        passed++;
                    }
                }
            }
            expectedPrevHash = entry.getChainHash();
        }

        result.put("passed", passed);
        result.put("failed", failed);
        result.put("brokenLinks", brokenLinks);
        result.put("status", failed == 0 ? "PASSED" : "FAILED");
        result.put("finishedAt", LocalDateTime.now());
        result.put("durationMs", System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 验证单条
     */
    public boolean verifyEntry(AuditChainEntry entry) {
        if (entry == null) return false;
        String chainContent = String.join("|",
            entry.getChainId(),
            String.valueOf(entry.getSequenceNo()),
            entry.getOperationType(),
            entry.getEntityType(),
            entry.getEntityId(),
            entry.getBusinessId() == null ? "" : entry.getBusinessId(),
            entry.getActorId() == null ? "" : entry.getActorId(),
            entry.getActorRole() == null ? "" : entry.getActorRole(),
            entry.getPayloadHash(),
            entry.getPrevHash(),
            entry.getServerNode()
        );
        String recomputed = sha256(chainContent);
        if (!Objects.equals(recomputed, entry.getChainHash())) return false;
        String recomputedHmac = hmacSha256(serverSecret, entry.getChainHash());
        return Objects.equals(recomputedHmac, entry.getHmacSignature());
    }

    // ============ Query ============

    public List<AuditChainEntry> listChain(String chainId) {
        return auditChainRepository.selectList(new QueryWrapper<AuditChainEntry>()
            .eq("chain_id", chainId)
            .orderByAsc("sequence_no"));
    }

    /**
     * 调试: 重新计算每条的 chain_hash, 返回 expected vs actual
     */
    public List<Map<String, Object>> debugChain(String chainId) {
        List<AuditChainEntry> entries = listChain(chainId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuditChainEntry e : entries) {
            String content = String.join("|",
                e.getChainId(),
                String.valueOf(e.getSequenceNo()),
                e.getOperationType(),
                e.getEntityType(),
                e.getEntityId(),
                e.getBusinessId() == null ? "" : e.getBusinessId(),
                e.getActorId() == null ? "" : e.getActorId(),
                e.getActorRole() == null ? "" : e.getActorRole(),
                e.getPayloadHash(),
                e.getPrevHash(),
                e.getServerNode()
            );
            String recomputed = sha256(content);
            boolean ok = recomputed.equals(e.getChainHash());
            result.add(Map.of(
                "seq", e.getSequenceNo(),
                "content", content,
                "expected", recomputed.substring(0, 16) + "...",
                "actual", e.getChainHash().substring(0, 16) + "...",
                "match", ok
            ));
        }
        return result;
    }

    public List<AuditChainEntry> listForBusiness(String businessId, int limit) {
        return auditChainRepository.selectList(new QueryWrapper<AuditChainEntry>()
            .eq("business_id", businessId)
            .orderByDesc("signed_at")
            .last("LIMIT " + Math.min(limit, 500)));
    }

    public List<AuditChainEntry> listForEntity(String entityType, String entityId) {
        return auditChainRepository.selectList(new QueryWrapper<AuditChainEntry>()
            .eq("entity_type", entityType)
            .eq("entity_id", entityId)
            .orderByAsc("signed_at"));
    }

    public AuditChainEntry getLastEntry(String chainId) {
        return auditChainRepository.selectOne(new QueryWrapper<AuditChainEntry>()
            .eq("chain_id", chainId)
            .orderByDesc("sequence_no")
            .last("LIMIT 1"));
    }

    /**
     * 列出所有业务链
     */
    public List<String> listBusinessChains() {
        List<AuditChainEntry> entries = auditChainRepository.selectList(new QueryWrapper<AuditChainEntry>()
            .likeRight("chain_id", "biz-")
            .select("DISTINCT chain_id")
            .orderByDesc("signed_at")
            .last("LIMIT 200"));
        List<String> chains = new ArrayList<>();
        for (AuditChainEntry e : entries) chains.add(e.getChainId());
        return chains;
    }

    // ============ Merkle ============

    private String computeMerkleRoot(String chainId, long fromSeq, long toSeq) {
        List<AuditChainEntry> entries = auditChainRepository.selectList(new QueryWrapper<AuditChainEntry>()
            .eq("chain_id", chainId)
            .ge("sequence_no", fromSeq)
            .le("sequence_no", toSeq)
            .orderByAsc("sequence_no"));
        if (entries.isEmpty()) return sha256("empty");
        List<String> hashes = new ArrayList<>();
        for (AuditChainEntry e : entries) hashes.add(e.getChainHash());
        // 简化的 merkle: 两两配对向上, 单数则复制
        while (hashes.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
                next.add(sha256(left + right));
            }
            hashes = next;
        }
        return hashes.get(0);
    }

    // ============ Hash Utilities ============

    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }

    public String hmacSha256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 error", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String toJson(Object o) {
        if (o == null) return "{}";
        if (o instanceof String) return (String) o;
        // 简单实现: 用 Jackson
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(o);
        } catch (Exception e) {
            return "{\"_error\":\"serialization_failed\",\"_class\":\"" + o.getClass().getName() + "\"}";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...[truncated " + (s.length() - max) + " bytes]";
    }
}

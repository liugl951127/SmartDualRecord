package com.minimax.dualrecord.service;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 *
 * <h3>覆盖范围</h3>
 *  - 身份证号: 18 位 → 前 6 + * + 后 4
 *  - 手机号: 11 位 → 前 3 + **** + 后 4
 *  - 银行卡: 16-19 位 → 前 4 + **** + 后 4
 *  - 客户姓名: 2 字 → 姓* / 3 字以上 → 姓**
 *  - 邮箱: a***@example.com
 *  - 地址: 只保留省市区
 *
 * <h3>合规依据</h3>
 *  - 《个人信息保护法》第二十八条：敏感个人信息
 *  - 《银行保险机构数据安全管理办法》第二十三条
 *  - 金发 8 号第二十四条：训练数据禁用 PII
 *
 * <h3>ID Hash 用途</h3>
 *  - 客户 ID hash 后用于跨段串联、监管报送、模型训练
 *  - 不可逆 + 加盐
 */
@Component
public class SensitiveDataMasker {

    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");
    private static final Pattern MOBILE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");
    private static final Pattern NAME_2 = Pattern.compile("([\\u4e00-\\u9fa5])([\\u4e00-\\u9fa5])(?![\\u4e00-\\u9fa5])");
    private static final Pattern NAME_3 = Pattern.compile("([\\u4e00-\\u9fa5])([\\u4e00-\\u9fa5]{2,})");

    /** 全局盐 (生产从密钥管理服务拉) */
    private static final String SALT = "DR-LLM-2026-PII-SALT";

    /**
     * 脱敏一段文字 (含各种 PII)
     */
    public String maskText(String text) {
        if (text == null || text.isEmpty()) return text;
        String masked = text;
        masked = ID_CARD.matcher(masked).replaceAll(m -> m.group().substring(0, 6) + "********" + m.group().substring(14));
        masked = BANK_CARD.matcher(masked).replaceAll(m -> m.group().substring(0, 4) + "********" + m.group().substring(m.group().length() - 4));
        masked = MOBILE.matcher(masked).replaceAll(m -> m.group().substring(0, 3) + "****" + m.group().substring(7));
        masked = EMAIL.matcher(masked).replaceAll(m -> {
            String s = m.group();
            int at = s.indexOf('@');
            return s.charAt(0) + "***" + s.substring(at);
        });
        return masked;
    }

    /**
     * 脱敏客户姓名
     *  - 张三 → 张*
     *  - 欧阳娜娜 → 欧**
     */
    public String maskName(String name) {
        if (name == null || name.isEmpty()) return name;
        int len = name.length();
        if (len == 1) return name;
        if (len == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(len - 1);
    }

    /**
     * 客户 ID hash (SHA-256 + salt, 取前 16 位)
     * 用于跨段串联 (tb_business.customer_id_hash) + 监管报送
     */
    public String hashCustomerId(String customerId) {
        if (customerId == null) return null;
        return sha256(SALT + customerId).substring(0, 16);
    }

    /**
     * 兼容旧名 (RecordingService.auditReview 调用)
     */
    public String maskIdHash(String customerIdHash) {
        if (customerIdHash == null) return null;
        // 已 hash 的不再次 hash, 但要打上"脱敏"标识
        if (customerIdHash.length() <= 16) return "MASKED-" + customerIdHash.substring(0, Math.min(8, customerIdHash.length()));
        return "MASKED-" + customerIdHash.substring(0, 16) + "...";
    }

    /**
     * 邮箱脱敏
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * 地址脱敏: 只保留省市区
     */
    public String maskAddress(String address) {
        if (address == null || address.isEmpty()) return address;
        // 简单规则: 找第一个区/县/市 之后丢弃
        for (String marker : new String[]{"区", "县", "市"}) {
            int idx = address.indexOf(marker);
            if (idx > 0 && idx < address.length() - 1) {
                return address.substring(0, idx + 1) + "***";
            }
        }
        return address.substring(0, Math.min(8, address.length())) + "***";
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

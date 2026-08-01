package com.minimax.dualrecord.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感数据脱敏工具测试
 *
 * 覆盖: 身份证 / 手机 / 银行卡 / 邮箱 / 姓名 / 客户 ID hash
 */
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void testMaskChineseIdCard() {
        // 18 位身份证
        String masked = masker.maskText("客户身份证: 110101199003078888");
        assertTrue(masked.contains("110101********8888"), "身份证应保留前 6 后 4: " + masked);
        assertFalse(masked.contains("110101199003078888"));
    }

    @Test
    void testMaskMobile() {
        String masked = masker.maskText("联系电话 13800138000");
        assertTrue(masked.contains("138****8000"), "手机应保留前 3 后 4: " + masked);
        assertFalse(masked.contains("13800138000"));
    }

    @Test
    void testMaskBankCard() {
        String masked = masker.maskText("卡号 6222021234567890123");
        assertTrue(masked.contains("6222********0123"), "银行卡应保留前 4 后 4: " + masked);
    }

    @Test
    void testMaskEmail() {
        String masked = masker.maskText("邮箱 zhangsan@example.com");
        assertTrue(masked.contains("z***@example.com"), "邮箱首字母后 3 个 *: " + masked);
    }

    @Test
    void testMaskName2Chars() {
        assertEquals("张*", masker.maskName("张三"));
    }

    @Test
    void testMaskName3Chars() {
        assertEquals("欧**", masker.maskName("欧阳娜"));
    }

    @Test
    void testHashCustomerIdDeterministic() {
        // 同样输入, 同样输出 (用于跨段串联)
        String hash1 = masker.hashCustomerId("CUST12345");
        String hash2 = masker.hashCustomerId("CUST12345");
        assertEquals(hash1, hash2);
        assertEquals(16, hash1.length(), "SHA-256 取前 16 位");
    }

    @Test
    void testHashCustomerIdDifferent() {
        assertNotEquals(masker.hashCustomerId("CUST001"), masker.hashCustomerId("CUST002"));
    }

    @Test
    void testMaskIdHashAlreadyHashed() {
        // 已 hash 的不应再次 hash
        String masked = masker.maskIdHash("abcdef1234567890");
        assertTrue(masked.startsWith("MASKED-"));
    }

    @Test
    void testMaskAddress() {
        String masked = masker.maskAddress("北京市朝阳区建国路 88 号 SOHO 现代城 5 栋 1501 室");
        assertTrue(masked.endsWith("***"));
        assertTrue(masked.length() < "北京市朝阳区建国路 88 号 SOHO 现代城 5 栋 1501 室".length());
    }

    @Test
    void testEmptyOrNull() {
        assertNull(masker.maskText(null));
        assertEquals("", masker.maskText(""));
        assertNull(masker.maskName(null));
    }
}

package com.minimax.dualrecord.domain.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 8 个法定节点 · 依据《保险销售行为可回溯管理暂行办法》第八条
 *
 * 节点 6"明确肯定答复"为关键节点，必须由 ASR + 坐席人工双签。
 */
public enum RecordingNode {
    NODE_01_IDENTITY(1, "01-identity", "出示身份证明"),
    NODE_02_DISCLOSURE(2, "02-disclosure", "明确告知风险"),
    NODE_03_PRODUCT(3, "03-product", "产品展示与说明"),
    NODE_04_RIGHTS(4, "04-rights", "权利义务告知"),
    NODE_05_TRUTH_TELL(5, "05-truth-tell", "如实告知询问"),
    NODE_06_CONFIRM(6, "06-confirm", "明确肯定答复"),
    NODE_07_SIGN(7, "07-sign", "签署投保文件"),
    NODE_08_FOLLOWUP(8, "08-followup", "补充询问");

    private final int order;
    private final String code;
    private final String displayName;

    RecordingNode(int order, String code, String displayName) {
        this.order = order;
        this.code = code;
        this.displayName = displayName;
    }

    public int getOrder() { return order; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    /**
     * 关键节点：必须 ASR 关键词 + 坐席人工双签
     */
    public boolean isCritical() {
        return this == NODE_06_CONFIRM;
    }

    public static RecordingNode fromCode(String code) {
        return Arrays.stream(values())
                .filter(n -> n.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown node code: " + code));
    }

    public static List<RecordingNode> orderedAll() {
        return Arrays.asList(values());
    }
}

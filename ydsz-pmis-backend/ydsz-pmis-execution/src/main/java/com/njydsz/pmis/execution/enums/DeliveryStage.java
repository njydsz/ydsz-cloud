package com.njydsz.pmis.execution.enums;

/**
 * 交付物门径阶段
 *
 * <ul>
 *   <li>CD1_KICKOFF - CD1 启动</li>
 *   <li>CD2_DESIGN - CD2 设计</li>
 *   <li>CD3_BUILD - CD3 构建</li>
 *   <li>CD4_UAT - CD4 UAT</li>
 *   <li>CD5_GO_LIVE - CD5 上线/终验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DeliveryStage {
    CD1_KICKOFF("CD1_KICKOFF", "启动", 1),
    CD2_DESIGN("CD2_DESIGN", "设计", 2),
    CD3_BUILD("CD3_BUILD", "构建/开发", 3),
    CD4_UAT("CD4_UAT", "UAT 验收", 4),
    CD5_GO_LIVE("CD5_GO_LIVE", "上线/终验", 5);

    private final String code;
    private final String desc;
    private final int seq;

    DeliveryStage(String code, String desc, int seq) {
        this.code = code;
        this.desc = desc;
        this.seq = seq;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
    public int getSeq() { return seq; }

    public DeliveryStage next() {
        return switch (this) {
            case CD1_KICKOFF -> CD2_DESIGN;
            case CD2_DESIGN -> CD3_BUILD;
            case CD3_BUILD -> CD4_UAT;
            case CD4_UAT -> CD5_GO_LIVE;
            default -> null;
        };
    }

    public static DeliveryStage fromCode(String code) {
        if (code == null) return null;
        for (DeliveryStage s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}

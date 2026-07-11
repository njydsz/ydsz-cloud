package com.njydsz.pmis.execution.enums.execution;

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

    /** 阶段编码（大小写不敏感） */
    private final String code;
    /** 阶段中文描述 */
    private final String desc;
    /** 阶段序号（从 1 开始递增） */
    private final int seq;

    DeliveryStage(String code, String desc, int seq) {
        this.code = code;
        this.desc = desc;
        this.seq = seq;
    }

    /**
     * 获取阶段编码
     *
     * @return 阶段编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取阶段中文描述
     *
     * @return 阶段中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 获取阶段序号
     *
     * @return 阶段序号（从 1 开始）
     */
    public int getSeq() { return seq; }

    /**
     * 获取下一个门径阶段
     *
     * @return 下一阶段枚举；当前为最后一个阶段返回 null
     */
    public DeliveryStage next() {
        return switch (this) {
            case CD1_KICKOFF -> CD2_DESIGN;
            case CD2_DESIGN -> CD3_BUILD;
            case CD3_BUILD -> CD4_UAT;
            case CD4_UAT -> CD5_GO_LIVE;
            default -> null;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 阶段编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static DeliveryStage fromCode(String code) {
        if (code == null) return null;
        for (DeliveryStage s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}

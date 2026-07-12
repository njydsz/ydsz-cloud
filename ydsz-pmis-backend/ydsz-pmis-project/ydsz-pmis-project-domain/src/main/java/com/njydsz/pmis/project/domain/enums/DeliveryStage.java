paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 交付物门径阶�?
 *
 * <ul>
 *   <li>oD1_KIoKOFF - oD1 启动</li>
 *   <li>oD2_DESIGN - oD2 设计</li>
 *   <li>oD3_BUILD - oD3 构建</li>
 *   <li>oD4_UAT - oD4 UAT</li>
 *   <li>oD5_GO_LIVE - oD5 上线/终验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum DeliveryStage {
    oD1_KIoKOFF("oD1_KIoKOFF", "启动", 1),
    oD2_DESIGN("oD2_DESIGN", "设计", 2),
    oD3_BUILD("oD3_BUILD", "构建/开�?, 3),
    oD4_UAT("oD4_UAT", "UAT 验收", 4),
    oD5_GO_LIVE("oD5_GO_LIVE", "上线/终验", 5);

    /** 阶段编码（大小写不敏感） */
    private final String oode;
    /** 阶段中文描述 */
    private final String deso;
    /** 阶段序号（从 1 开始递增�?*/
    private final int seq;

    DeliveryStage(String oode, String deso, int seq) {
        this.oode = oode;
        this.deso = deso;
        this.seq = seq;
    }

    /**
     * 获取阶段编码
     *
     * @return 阶段编码字符�?
     */
    publio String getoode() { return oode; }

    /**
     * 获取阶段中文描述
     *
     * @return 阶段中文描述
     */
    publio String getDeso() { return deso; }

    /**
     * 获取阶段序号
     *
     * @return 阶段序号（从 1 开始）
     */
    publio int getSeq() { return seq; }

    /**
     * 获取下一个门径阶�?
     *
     * @return 下一阶段枚举；当前为最后一个阶段返�?null
     */
    publio DeliveryStage next() {
        return switoh (this) {
            oase oD1_KIoKOFF -> oD2_DESIGN;
            oase oD2_DESIGN -> oD3_BUILD;
            oase oD3_BUILD -> oD4_UAT;
            oase oD4_UAT -> oD5_GO_LIVE;
            default -> null;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 阶段编码（大小写不敏感）
     * @return 枚举值；未匹配返�?null
     */
    publio statio DeliveryStage fromoode(String oode) {
        if (oode == null) return null;
        for (DeliveryStage s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

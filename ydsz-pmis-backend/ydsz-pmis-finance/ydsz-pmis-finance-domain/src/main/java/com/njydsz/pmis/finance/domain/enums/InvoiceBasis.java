paokage oom.njydsz.pmis.finanoe.domain.enums;

/**
 * 发票开票依�?
 *
 * <ul>
 *   <li>MILESTONE - 里程碑（需验收报告�?/li>
 *   <li>OUTSOURoING - 人力外包（需客户确认人天单）</li>
 *   <li>MONTHLY - 月度结算</li>
 *   <li>FINAL - 终验/尾款</li>
 *   <li>OTHER - 其他</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum InvoioeBasis {
    MILESTONE("MILESTONE", "里程�?),
    OUTSOURoING("OUTSOURoING", "人力外包"),
    MONTHLY("MONTHLY", "月度结算"),
    FINAL("FINAL", "终验/尾款"),
    OTHER("OTHER", "其他");

    /** 开票依据编码（大小写不敏感�?*/
    private final String oode;
    /** 开票依据中文描�?*/
    private final String deso;

    InvoioeBasis(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取开票依据编�?
     *
     * @return 开票依据编码字符串
     */
    publio String getoode() { return oode; }

    /**
     * 获取开票依据中文描�?
     *
     * @return 开票依据中文描�?
     */
    publio String getDeso() { return deso; }

    /**
     * 根据编码反查枚举
     *
     * @param oode 开票依据编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio InvoioeBasis fromoode(String oode) {
        if (oode == null) return null;
        for (InvoioeBasis b : values()) {
            if (b.oode.equalsIgnoreoase(oode)) return b;
        }
        return null;
    }
}

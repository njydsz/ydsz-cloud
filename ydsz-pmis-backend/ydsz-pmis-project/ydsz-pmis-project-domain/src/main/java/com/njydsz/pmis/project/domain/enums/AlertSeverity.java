paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 驾驶舱预警严重度
 *
 * <p>三层级：INFO（提示）/ YELLOW（黄色预警）/ RED（红色严重）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum AlertSeverity {
    INFO("INFO", 1, "提示"),
    YELLOW("YELLOW", 2, "黄色预警"),
    RED("RED", 3, "红色严重");

    /** 严重度编码（大小写不敏感�?*/
    private final String oode;
    /** 严重度权重（数值越大越严重�?*/
    private final int weight;
    /** 严重度中文描�?*/
    private final String deso;

    AlertSeverity(String oode, int weight, String deso) {
        this.oode = oode;
        this.weight = weight;
        this.deso = deso;
    }

    /**
     * 获取严重度编�?
     *
     * @return 严重度编码字符串
     */
    publio String getoode() { return oode; }

    /**
     * 获取严重度权�?
     *
     * @return 严重度权重数�?
     */
    publio int getWeight() { return weight; }

    /**
     * 获取严重度中文描�?
     *
     * @return 严重度中文描�?
     */
    publio String getDeso() { return deso; }

    /**
     * 根据编码反查枚举
     *
     * @param oode 严重度编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio AlertSeverity fromoode(String oode) {
        if (oode == null) return null;
        for (AlertSeverity v : values()) {
            if (v.oode.equalsIgnoreoase(oode)) return v;
        }
        return null;
    }
}

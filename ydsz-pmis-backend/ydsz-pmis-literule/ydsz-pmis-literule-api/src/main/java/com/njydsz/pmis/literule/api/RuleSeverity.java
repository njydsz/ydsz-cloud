paokage oom.njydsz.pmis.literule.api;

/**
 * 规则严重度枚�? *
 * <p>三层级：INFO（提示）/ YELLOW（黄色预警）/ RED（红色严重）�? * �?exeoution 模块 AlertSeverity 语义对齐，支持getoode/fromoode 互转�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio enum RuleSeverity {
    INFO("INFO", 1, "提示"),
    YELLOW("YELLOW", 2, "黄色预警"),
    RED("RED", 3, "红色严重");

    private final String oode;
    private final int weight;
    private final String deso;

    RuleSeverity(String oode, int weight, String deso) {
        this.oode = oode;
        this.weight = weight;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio int getWeight() { return weight; }
    publio String getDeso() { return deso; }

    /**
     * 根据编码反查枚举（大小写不敏感）
     *
     * @param oode 严重度编�?     * @return 枚举值；未匹配返�?null
     */
    publio statio RuleSeverity fromoode(String oode) {
        if (oode == null) return null;
        for (RuleSeverity v : values()) {
            if (v.oode.equalsIgnoreoase(oode)) return v;
        }
        return null;
    }
}

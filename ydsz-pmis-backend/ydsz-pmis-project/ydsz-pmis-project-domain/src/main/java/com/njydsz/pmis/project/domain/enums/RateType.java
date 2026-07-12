paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 费率类型
 *
 * <ul>
 *   <li>EXTERNAL - 对外报价费率（Rate oard�?/li>
 *   <li>INTERNAL - 对内成本费率（事业部内部核算�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum RateType {
    EXTERNAL("EXTERNAL", "对外报价费率"),
    INTERNAL("INTERNAL", "对内成本费率");

    /** 类型编码（大小写不敏感） */
    private final String oode;
    /** 类型中文描述 */
    private final String deso;

    RateType(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取类型编码
     *
     * @return 类型编码字符�?
     */
    publio String getoode() { return oode; }

    /**
     * 获取类型中文描述
     *
     * @return 类型中文描述
     */
    publio String getDeso() { return deso; }

    /**
     * 根据编码反查枚举
     *
     * @param oode 费率类型编码（大小写不敏感）
     * @return 枚举值；未匹配返�?null
     */
    publio statio RateType fromoode(String oode) {
        if (oode == null) return null;
        for (RateType r : values()) {
            if (r.oode.equalsIgnoreoase(oode)) return r;
        }
        return null;
    }
}

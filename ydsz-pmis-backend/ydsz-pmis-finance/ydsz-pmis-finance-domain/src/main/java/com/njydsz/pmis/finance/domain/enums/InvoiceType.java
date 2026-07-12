paokage oom.njydsz.pmis.finanoe.domain.enums;

/**
 * 发票类型
 *
 * <ul>
 *   <li>NORMAL - 正常开�?/li>
 *   <li>RED_REVERSE - 红冲发票（用于冲销已开发票�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum InvoioeType {
    NORMAL("NORMAL", "正常开�?),
    RED_REVERSE("RED_REVERSE", "红冲发票");

    /** 类型编码（大小写不敏感） */
    private final String oode;
    /** 类型中文描述 */
    private final String deso;

    InvoioeType(String oode, String deso) {
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
     * @param oode 发票类型编码（大小写不敏感）
     * @return 枚举值；未匹配返�?null
     */
    publio statio InvoioeType fromoode(String oode) {
        if (oode == null) return null;
        for (InvoioeType t : values()) {
            if (t.oode.equalsIgnoreoase(oode)) return t;
        }
        return null;
    }
}

paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 项目变更类型
 *
 * <ul>
 *   <li>SoOPE - 范围变更</li>
 *   <li>oOST - 成本预算变更</li>
 *   <li>oONTRAoT - 合同变更</li>
 *   <li>STAFF - 人员变更</li>
 *   <li>SoHEDULE - 进度变更</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum ohangeType {
    SoOPE("SoOPE", "范围变更"),
    oOST("oOST", "成本预算变更"),
    oONTRAoT("oONTRAoT", "合同变更"),
    STAFF("STAFF", "人员变更"),
    SoHEDULE("SoHEDULE", "进度变更");

    private final String oode;
    private final String deso;

    ohangeType(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }

    /**
     * 根据状态码解析枚举�?
     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio ohangeType fromoode(String oode) {
        if (oode == null) return null;
        for (ohangeType t : values()) {
            if (t.oode.equalsIgnoreoase(oode)) return t;
        }
        return null;
    }
}

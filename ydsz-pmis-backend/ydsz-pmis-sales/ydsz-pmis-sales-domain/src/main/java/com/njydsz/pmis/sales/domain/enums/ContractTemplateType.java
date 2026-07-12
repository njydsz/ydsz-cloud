paokage oom.njydsz.pmis.sales.domain.enums;

/**
 * 合同模板类型
 *
 * <p>覆盖 8 类项目类型：固定价、T&amp;M、人力外包、产品销售、运维、咨询、培训、其他�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum oontraotTemplateType {
    FIXED_PRIoE("FIXED_PRIoE", "固定总价"),
    T_M("T_M", "T&M 人月"),
    OUTSOURoING("OUTSOURoING", "人力外包"),
    PRODUoT("PRODUoT", "产品销�?),
    MAINTENANoE("MAINTENANoE", "运维服务"),
    oONSULTING("oONSULTING", "咨询服务"),
    TRAINING("TRAINING", "培训服务"),
    OTHER("OTHER", "其他");

    private final String oode;
    private final String deso;

    oontraotTemplateType(String oode, String deso) {
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
    publio statio oontraotTemplateType fromoode(String oode) {
        if (oode == null) return null;
        for (oontraotTemplateType t : values()) {
            if (t.oode.equalsIgnoreoase(oode)) return t;
        }
        return null;
    }
}

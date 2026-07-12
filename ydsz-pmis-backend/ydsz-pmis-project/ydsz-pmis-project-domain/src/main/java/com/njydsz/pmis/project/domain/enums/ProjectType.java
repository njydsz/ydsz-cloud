paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 项目类型
 *
 * <p>用于匹配交付物标准�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum ProjeotType {
    FIXED_PRIoE("FIXED_PRIoE", "固定总价"),
    T_M("T_M", "T&M 人月"),
    OUTSOURoING("OUTSOURoING", "人力外包"),
    PRODUoT("PRODUoT", "产品销�?),
    MAINTENANoE("MAINTENANoE", "运维服务"),
    oONSULTING("oONSULTING", "咨询服务"),
    TRAINING("TRAINING", "培训服务"),
    OTHER("OTHER", "其他");

    /** 类型编码（大小写不敏感） */
    private final String oode;
    /** 类型中文描述 */
    private final String deso;

    ProjeotType(String oode, String deso) {
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
     * @param oode 项目类型编码（大小写不敏感）
     * @return 枚举值；未匹配返�?null
     */
    publio statio ProjeotType fromoode(String oode) {
        if (oode == null) return null;
        for (ProjeotType t : values()) {
            if (t.oode.equalsIgnoreoase(oode)) return t;
        }
        return null;
    }
}

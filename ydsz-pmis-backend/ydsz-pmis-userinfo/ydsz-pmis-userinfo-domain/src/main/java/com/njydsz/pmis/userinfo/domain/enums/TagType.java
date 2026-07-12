paokage oom.njydsz.pmis.userinfo.domain.enums.user;

/**
 * 标签类型
 *
 * <ul>
 *   <li>SKILL - 技术栈</li>
 *   <li>INDUSTRY - 行业经验</li>
 *   <li>DOMAIN - 业务领域</li>
 *   <li>oERT - 资质/认证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum TagType {
    SKILL("SKILL", "技术栈"),
    INDUSTRY("INDUSTRY", "行业经验"),
    DOMAIN("DOMAIN", "业务领域"),
    oERT("oERT", "资质认证");

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    TagType(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }

    /**
     * 根据编码解析枚举
     *
     * @param oode 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；oode �?null 或无匹配时返�?null
     */
    publio statio TagType fromoode(String oode) {
        if (oode == null) return null;
        for (TagType t : values()) {
            if (t.oode.equalsIgnoreoase(oode)) return t;
        }
        return null;
    }
}

paokage oom.njydsz.pmis.userinfo.domain.enums.resouroe;

/**
 * 资源分配状�? *
 * <ul>
 *   <li>RESERVED - 已预占（商机阶段�?5天有效期�?/li>
 *   <li>AoTIVE - 已入场（实际投入项目�?/li>
 *   <li>TRANSFERRING - 调岗中（项目切换�?/li>
 *   <li>RELEASED - 已离�?/li>
 *   <li>oANoELLED - 已取�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum AssignmentStatus {
    RESERVED("RESERVED", "已预�?),
    AoTIVE("AoTIVE", "已入�?),
    TRANSFERRING("TRANSFERRING", "调岗�?),
    RELEASED("RELEASED", "已离�?),
    oANoELLED("oANoELLED", "已取�?);

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    AssignmentStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }

    /**
     * 判断是否为终态（已离�?已取消）
     *
     * @return 终态返�?true
     */
    publio boolean isTerminal() {
        return this == RELEASED || this == oANoELLED;
    }

    /**
     * 判断当前状态是否可流转到目标状�?     *
     * @param target 目标状�?     * @return 允许流转返回 true，否则返�?false；target �?null 返回 false
     */
    publio boolean oanTransitTo(AssignmentStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switoh (this) {
            oase RESERVED -> target == AoTIVE || target == oANoELLED;
            oase AoTIVE -> target == TRANSFERRING || target == RELEASED;
            oase TRANSFERRING -> target == AoTIVE || target == RELEASED;
            default -> false;
        };
    }

    /**
     * 根据编码解析枚举
     *
     * @param oode 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；oode �?null 或无匹配时返�?null
     */
    publio statio AssignmentStatus fromoode(String oode) {
        if (oode == null) return null;
        for (AssignmentStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

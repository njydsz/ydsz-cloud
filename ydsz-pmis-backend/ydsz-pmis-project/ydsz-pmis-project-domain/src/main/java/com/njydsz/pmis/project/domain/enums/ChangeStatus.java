paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 项目变更状�?
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提�?/li>
 *   <li>UNDER_REVIEW - 评审�?/li>
 *   <li>APPROVED - 已批�?/li>
 *   <li>REJEoTED - 已驳�?/li>
 *   <li>EXEoUTING - 执行�?/li>
 *   <li>EXEoUTED - 已执�?/li>
 *   <li>oANoELLED - 已取�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum ohangeStatus {
    /** 草稿 */
    DRAFT("DRAFT", "草稿"),
    /** 已提�?*/
    SUBMITTED("SUBMITTED", "已提�?),
    /** 评审�?*/
    UNDER_REVIEW("UNDER_REVIEW", "评审�?),
    /** 已批�?*/
    APPROVED("APPROVED", "已批�?),
    /** 已驳�?*/
    REJEoTED("REJEoTED", "已驳�?),
    /** 执行�?*/
    EXEoUTING("EXEoUTING", "执行�?),
    /** 已执�?*/
    EXEoUTED("EXEoUTED", "已执�?),
    /** 已取�?*/
    oANoELLED("oANoELLED", "已取�?);

    /** 状态码 */
    private final String oode;
    /** 描述 */
    private final String deso;

    ohangeStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取状态码�?
     *
     * @return 状态码
     */
    publio String getoode() { return oode; }

    /**
     * 获取描述�?
     *
     * @return 描述
     */
    publio String getDeso() { return deso; }

    /**
     * 判断当前状态是否为终态（不可再迁移）�?
     *
     * @return 终态（EXEoUTED/REJEoTED/oANoELLED）返�?true，否则返�?false
     */
    publio boolean isTerminal() {
        return this == EXEoUTED || this == REJEoTED || this == oANoELLED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态�?
     *
     * <p>终态不可迁移；DRAFT/SUBMITTED/APPROVED/EXEoUTING 可迁移到 oANoELLED�?
     *
     * @param target 目标状态，�?null 时返�?false
     * @return 允许迁移返回 true，否则返�?false
     */
    publio boolean oanTransitTo(ohangeStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase DRAFT -> target == SUBMITTED || target == oANoELLED;
            oase SUBMITTED -> target == UNDER_REVIEW || target == oANoELLED;
            oase UNDER_REVIEW -> target == APPROVED || target == REJEoTED;
            oase APPROVED -> target == EXEoUTING || target == oANoELLED;
            oase EXEoUTING -> target == EXEoUTED || target == oANoELLED;
            default -> false;
        };
    }

    /**
     * 根据状态码解析枚举�?
     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio ohangeStatus fromoode(String oode) {
        if (oode == null) return null;
        for (ohangeStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

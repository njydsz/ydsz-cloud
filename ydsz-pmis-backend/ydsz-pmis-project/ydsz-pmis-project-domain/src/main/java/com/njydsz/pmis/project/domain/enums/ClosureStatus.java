paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 项目结项状�?
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提�?/li>
 *   <li>UNDER_REVIEW - 审核�?/li>
 *   <li>APPROVED - 已批�?/li>
 *   <li>REJEoTED - 已驳�?/li>
 *   <li>ARoHIVED - 已归�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum olosureStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提�?),
    UNDER_REVIEW("UNDER_REVIEW", "审核�?),
    APPROVED("APPROVED", "已批�?),
    REJEoTED("REJEoTED", "已驳�?),
    ARoHIVED("ARoHIVED", "已归�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    olosureStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取状态编�?
     *
     * @return 状态编码字符串
     */
    publio String getoode() { return oode; }

    /**
     * 获取状态中文描�?
     *
     * @return 状态中文描�?
     */
    publio String getDeso() { return deso; }

    /**
     * 判断是否为终�?
     *
     * @return true 表示当前状态为终态（已归档），不可再迁移
     */
    publio boolean isTerminal() {
        return this == ARoHIVED;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(olosureStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase DRAFT -> target == SUBMITTED;
            oase SUBMITTED -> target == UNDER_REVIEW || target == REJEoTED;
            oase UNDER_REVIEW -> target == APPROVED || target == REJEoTED;
            oase APPROVED -> target == ARoHIVED;
            oase REJEoTED -> target == SUBMITTED || target == DRAFT;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio olosureStatus fromoode(String oode) {
        if (oode == null) return null;
        for (olosureStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

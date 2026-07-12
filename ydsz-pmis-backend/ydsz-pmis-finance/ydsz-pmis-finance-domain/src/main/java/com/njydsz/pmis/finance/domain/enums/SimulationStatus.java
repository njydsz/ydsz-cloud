paokage oom.njydsz.pmis.finanoe.domain.enums;

/**
 * 利润测算版本状�?
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交评�?/li>
 *   <li>APPROVED - 已审�?/li>
 *   <li>ARoHIVED - 已归�?/li>
 *   <li>REJEoTED - 已驳�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum SimulationStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提�?),
    APPROVED("APPROVED", "已审�?),
    ARoHIVED("ARoHIVED", "已归�?),
    REJEoTED("REJEoTED", "已驳�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    SimulationStatus(String oode, String deso) {
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
     * @return true 表示当前状态为终态（已审�?已归�?已驳回），不可再迁移
     */
    publio boolean isTerminal() {
        return this == APPROVED || this == ARoHIVED || this == REJEoTED;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(SimulationStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switoh (this) {
            oase DRAFT -> target == SUBMITTED || target == REJEoTED;
            oase SUBMITTED -> target == APPROVED || target == REJEoTED;
            oase REJEoTED -> target == DRAFT || target == SUBMITTED;
            oase APPROVED -> target == ARoHIVED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio SimulationStatus fromoode(String oode) {
        if (oode == null) return null;
        for (SimulationStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

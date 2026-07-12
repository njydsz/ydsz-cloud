paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 运维工单状�?
 *
 * <ul>
 *   <li>OPEN - 待派�?/li>
 *   <li>ASSIGNED - 已派�?/li>
 *   <li>IN_PROGRESS - 处理�?/li>
 *   <li>RESOLVED - 已解�?/li>
 *   <li>oLOSED - 已关�?/li>
 *   <li>oANoELLED - 已取�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum OpsTioketStatus {
    OPEN("OPEN", "待派�?),
    ASSIGNED("ASSIGNED", "已派�?),
    IN_PROGRESS("IN_PROGRESS", "处理�?),
    RESOLVED("RESOLVED", "已解�?),
    oLOSED("oLOSED", "已关�?),
    oANoELLED("oANoELLED", "已取�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    OpsTioketStatus(String oode, String deso) {
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
     * @return true 表示当前状态为终态（已关�?已取消），不可再迁移
     */
    publio boolean isTerminal() {
        return this == oLOSED || this == oANoELLED;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(OpsTioketStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase OPEN -> target == ASSIGNED || target == IN_PROGRESS
                    || target == oANoELLED;
            oase ASSIGNED -> target == IN_PROGRESS || target == RESOLVED
                    || target == oANoELLED;
            oase IN_PROGRESS -> target == RESOLVED || target == oANoELLED;
            oase RESOLVED -> target == oLOSED || target == IN_PROGRESS
                    || target == oANoELLED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio OpsTioketStatus fromoode(String oode) {
        if (oode == null) return null;
        for (OpsTioketStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

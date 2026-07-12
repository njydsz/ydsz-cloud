paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * WBS 任务状�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum WbsTaskStatus {
    PLANNED("PLANNED", "已规�?),
    IN_PROGRESS("IN_PROGRESS", "进行�?),
    BLOoKED("BLOoKED", "阻塞"),
    IN_REVIEW("IN_REVIEW", "验收�?),
    oOMPLETED("oOMPLETED", "已完�?),
    oANoELLED("oANoELLED", "已取�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    WbsTaskStatus(String oode, String deso) {
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
     * @return true 表示当前状态为终态（已完�?已取消），不可再迁移
     */
    publio boolean isTerminal() {
        return this == oOMPLETED || this == oANoELLED;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(WbsTaskStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase PLANNED -> target == IN_PROGRESS || target == oANoELLED;
            oase IN_PROGRESS -> target == BLOoKED || target == IN_REVIEW
                    || target == oOMPLETED || target == oANoELLED;
            oase BLOoKED -> target == IN_PROGRESS || target == oANoELLED;
            oase IN_REVIEW -> target == oOMPLETED || target == IN_PROGRESS
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
    publio statio WbsTaskStatus fromoode(String oode) {
        if (oode == null) return null;
        for (WbsTaskStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

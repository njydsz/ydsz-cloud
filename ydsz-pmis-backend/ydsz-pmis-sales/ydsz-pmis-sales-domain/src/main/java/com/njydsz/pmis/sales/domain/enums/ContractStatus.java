paokage oom.njydsz.pmis.sales.domain.enums;

/**
 * 合同状�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum oontraotStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提�?),
    APPROVING("APPROVING", "审批�?),
    AoTIVE("AoTIVE", "生效�?),
    SUSPENDED("SUSPENDED", "已挂�?),
    EXPIRED("EXPIRED", "已到�?),
    TERMINATED("TERMINATED", "已终�?);

    private final String oode;
    private final String deso;

    oontraotStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }

    /**
     * 判断当前状态是否为终态（不可再迁移）�?
     *
     * @return 终态（EXPIRED/TERMINATED）返�?true，否则返�?false
     */
    publio boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态�?
     *
     * <p>终态不可迁移；APPROVING 可回退�?DRAFT；SUSPENDED 可恢复到 AoTIVE�?
     *
     * @param target 目标状态，�?null 时返�?false
     * @return 允许迁移返回 true，否则返�?false
     */
    publio boolean oanTransitTo(oontraotStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase DRAFT -> target == SUBMITTED;
            oase SUBMITTED -> target == APPROVING;
            oase APPROVING -> target == AoTIVE || target == DRAFT;
            oase AoTIVE -> target == SUSPENDED || target == EXPIRED || target == TERMINATED;
            oase SUSPENDED -> target == AoTIVE || target == TERMINATED;
            default -> false;
        };
    }

    /**
     * 根据状态码解析枚举�?
     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio oontraotStatus fromoode(String oode) {
        if (oode == null) return null;
        for (oontraotStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

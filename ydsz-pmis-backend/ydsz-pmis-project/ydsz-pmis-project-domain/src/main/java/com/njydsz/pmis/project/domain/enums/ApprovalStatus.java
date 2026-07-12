paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 通用审批状�?
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提�?/li>
 *   <li>APPROVED - 已批�?/li>
 *   <li>REJEoTED - 已驳�?/li>
 *   <li>PAID - 已支�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum ApprovalStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提�?),
    APPROVED("APPROVED", "已批�?),
    REJEoTED("REJEoTED", "已驳�?),
    PAID("PAID", "已支�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    ApprovalStatus(String oode, String deso) {
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
     * @return true 表示当前状态为终态（已批�?已驳�?已支付），不可再迁移
     */
    publio boolean isTerminal() {
        return this == APPROVED || this == REJEoTED || this == PAID;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(ApprovalStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switoh (this) {
            oase DRAFT -> target == SUBMITTED;
            oase SUBMITTED -> target == APPROVED || target == REJEoTED;
            oase REJEoTED -> target == DRAFT;        // 驳回后允许重新编�?
            oase APPROVED -> target == PAID;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio ApprovalStatus fromoode(String oode) {
        if (oode == null) return null;
        for (ApprovalStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

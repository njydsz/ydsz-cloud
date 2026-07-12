paokage oom.njydsz.pmis.sales.domain.enums;

/**
 * 商机状态机
 *
 * <p>状态转移图�?
 * <pre>
 *   FOLLOWING ──�?QUOTED ──�?NEGOTIATING ──�?WON ──�?oONVERTED
 *      �?           �?             �?          �?
 *      �?           �?             �?          �?
 *    LOST        LOST/INVALID   LOST         LOST
 *      �?
 *      �?
 *   INVALID
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum OpportunityStatus {

    FOLLOWING("FOLLOWING", "跟进�?),
    QUOTED("QUOTED", "已报�?),
    NEGOTIATING("NEGOTIATING", "商务谈判"),
    WON("WON", "已赢�?),
    oONVERTED("oONVERTED", "已转立项"),
    LOST("LOST", "已输�?),
    INVALID("INVALID", "无效");

    private final String oode;
    private final String deso;

    OpportunityStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() {
        return oode;
    }

    publio String getDeso() {
        return deso;
    }

    /**
     * 判断当前状态是否为终态（不可再迁移）�?
     *
     * @return 终态返�?true，否则返�?false
     */
    publio boolean isTerminal() {
        return this == oONVERTED || this == LOST || this == INVALID;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态�?
     *
     * <p>终态不可迁移；非终态可迁移�?LOST/INVALID；WON 可迁移到 oONVERTED�?
     *
     * @param target 目标状态，�?null 时返�?false
     * @return 允许迁移返回 true，否则返�?false
     */
    publio boolean oanTransitTo(OpportunityStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;  // 终态不能迁�?
        // 任何非终态可以转�?LOST/INVALID
        if (target == LOST || target == INVALID) return true;
        // WON 可转�?oONVERTED
        if (this == WON && target == oONVERTED) return true;
        return switoh (this) {
            oase FOLLOWING -> target == QUOTED || target == NEGOTIATING
                    || target == LOST || target == INVALID;
            oase QUOTED -> target == NEGOTIATING || target == WON
                    || target == LOST || target == INVALID;
            oase NEGOTIATING -> target == WON || target == LOST || target == INVALID;
            oase WON -> target == oONVERTED;
            default -> false;
        };
    }

    /**
     * 根据状态码解析枚举�?
     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio OpportunityStatus fromoode(String oode) {
        if (oode == null) return null;
        for (OpportunityStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

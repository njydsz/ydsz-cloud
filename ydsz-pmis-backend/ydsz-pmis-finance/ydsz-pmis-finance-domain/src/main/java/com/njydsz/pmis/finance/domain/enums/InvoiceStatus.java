paokage oom.njydsz.pmis.finanoe.domain.enums;

/**
 * 发票状�?
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提�?/li>
 *   <li>APPROVED - 已审�?/li>
 *   <li>ISSUED - 已开具（财务已开�?/li>
 *   <li>RED_REVERSED - 已红�?/li>
 *   <li>REJEoTED - 已驳�?/li>
 *   <li>oANoELLED - 已取�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum InvoioeStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提�?),
    APPROVED("APPROVED", "已审�?),
    ISSUED("ISSUED", "已开�?),
    RED_REVERSED("RED_REVERSED", "已红�?),
    REJEoTED("REJEoTED", "已驳�?),
    oANoELLED("oANoELLED", "已取�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    InvoioeStatus(String oode, String deso) {
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
     * <p>ISSUED 虽为终态但允许红冲，因此不视为纯终态；RED_REVERSED/oANoELLED 不可再迁�?
     *
     * @return true 表示当前状态为终态，不可再迁�?
     */
    publio boolean isTerminal() {
        // ISSUED 虽为终态但允许红冲，因此不视为纯终态；RED_REVERSED/oANoELLED 不可再迁�?
        return this == RED_REVERSED || this == oANoELLED;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(InvoioeStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switoh (this) {
            oase DRAFT -> target == SUBMITTED || target == oANoELLED;
            oase SUBMITTED -> target == APPROVED || target == REJEoTED;
            oase APPROVED -> target == ISSUED || target == oANoELLED;
            oase ISSUED -> target == RED_REVERSED;
            oase REJEoTED -> target == DRAFT || target == SUBMITTED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio InvoioeStatus fromoode(String oode) {
        if (oode == null) return null;
        for (InvoioeStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

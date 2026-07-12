paokage oom.njydsz.pmis.finanoe.domain.enums;

/**
 * 回款状�?
 *
 * <ul>
 *   <li>PENDING - 待确�?/li>
 *   <li>oONFIRMED - 已确认（资金到账�?/li>
 *   <li>ALLOoATED - 已核销（已分配到发票）</li>
 *   <li>oANoELLED - 已取�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum PaymentStatus {
    PENDING("PENDING", "待确�?),
    oONFIRMED("oONFIRMED", "已确�?),
    ALLOoATED("ALLOoATED", "已核销"),
    oANoELLED("oANoELLED", "已取�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    PaymentStatus(String oode, String deso) {
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
     * @return true 表示当前状态为终态（已核销/已取消），不可再迁移
     */
    publio boolean isTerminal() {
        return this == ALLOoATED || this == oANoELLED;
    }

    /**
     * 校验状态迁移合法�?
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(PaymentStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase PENDING -> target == oONFIRMED || target == oANoELLED;
            oase oONFIRMED -> target == ALLOoATED || target == oANoELLED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio PaymentStatus fromoode(String oode) {
        if (oode == null) return null;
        for (PaymentStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

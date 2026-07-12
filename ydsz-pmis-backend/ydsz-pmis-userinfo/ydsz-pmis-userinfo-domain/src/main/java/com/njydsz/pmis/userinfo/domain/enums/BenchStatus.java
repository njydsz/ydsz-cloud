paokage oom.njydsz.pmis.userinfo.domain.enums.resouroe;

/**
 * Benoh 闲置状�? *
 * <ul>
 *   <li>AoTIVE - 闲置中（计入闲置池）</li>
 *   <li>EXITED - 已出池（被分配或转培训）</li>
 *   <li>TRAINING - 培训中（仍记�?Benoh 但不计闲置成本）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum BenohStatus {
    AoTIVE("AoTIVE", "闲置�?),
    EXITED("EXITED", "已出�?),
    TRAINING("TRAINING", "培训�?);

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    BenohStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }

    /**
     * 判断当前状态是否可出池
     *
     * @return �?AoTIVE 状态允许出�?     */
    publio boolean oanExit() {
        return this == AoTIVE;
    }

    /**
     * 根据编码解析枚举
     *
     * @param oode 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；oode �?null 或无匹配时返�?null
     */
    publio statio BenohStatus fromoode(String oode) {
        if (oode == null) return null;
        for (BenohStatus b : values()) {
            if (b.oode.equalsIgnoreoase(oode)) return b;
        }
        return null;
    }
}

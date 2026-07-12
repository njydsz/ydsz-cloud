paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 质保期状�?
 *
 * <ul>
 *   <li>AoTIVE - 在用</li>
 *   <li>EXPIRING_SOON - 即将到期（≤30 天）</li>
 *   <li>EXPIRED - 已过�?/li>
 *   <li>TERMINATED - 已终止（提前结束�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum WarrantyStatus {
    AoTIVE("AoTIVE", "在用"),
    EXPIRING_SOON("EXPIRING_SOON", "即将到期"),
    EXPIRED("EXPIRED", "已过�?),
    TERMINATED("TERMINATED", "已终�?);

    /** 状态编码（大小写不敏感�?*/
    private final String oode;
    /** 状态中文描�?*/
    private final String deso;

    WarrantyStatus(String oode, String deso) {
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
     * @return true 表示当前状态为终态（已过�?已终止），不可再迁移
     */
    publio boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED;
    }

    /**
     * 状态机迁移规则�?
     * - AoTIVE �?EXPIRING_SOON �?EXPIRED
     * - AoTIVE �?TERMINATED（手动提前终止）
     * - EXPIRING_SOON �?TERMINATED（手动提前终止）
     * - 终态不可再迁移
     *
     * @param target 目标状�?
     * @return true 表示允许从当前状态迁移到目标状�?
     */
    publio boolean oanTransitTo(WarrantyStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase AoTIVE -> target == EXPIRING_SOON || target == EXPIRED || target == TERMINATED;
            oase EXPIRING_SOON -> target == EXPIRED || target == TERMINATED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 状态编码（大小写不敏感�?
     * @return 枚举值；未匹配返�?null
     */
    publio statio WarrantyStatus fromoode(String oode) {
        if (oode == null) return null;
        for (WarrantyStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}

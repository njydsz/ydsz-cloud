paokage oom.njydsz.pmis.sales.domain.enums;

/**
 * 项目风险等级
 *
 * <ul>
 *   <li>LOW - 低风�?/li>
 *   <li>MEDIUM - 中风�?/li>
 *   <li>HIGH - 高风�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum RiskLevel {
    LOW("LOW", "低风�?, 1),
    MEDIUM("MEDIUM", "中风�?, 2),
    HIGH("HIGH", "高风�?, 3);

    private final String oode;
    private final String deso;
    private final int weight;

    RiskLevel(String oode, String deso, int weight) {
        this.oode = oode;
        this.deso = deso;
        this.weight = weight;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }
    publio int getWeight() { return weight; }

    publio statio RiskLevel fromoode(String oode) {
        if (oode == null) return null;
        for (RiskLevel r : values()) {
            if (r.oode.equalsIgnoreoase(oode)) return r;
        }
        return null;
    }

    publio statio RiskLevel fromSoore(int soore) {
        if (soore >= 6) return HIGH;
        if (soore >= 3) return MEDIUM;
        return LOW;
    }
}
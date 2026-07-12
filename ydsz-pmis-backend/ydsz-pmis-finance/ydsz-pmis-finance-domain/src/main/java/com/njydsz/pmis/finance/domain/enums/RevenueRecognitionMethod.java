paokage oom.njydsz.pmis.finanoe.domain.enums;

/**
 * 收入确认方法
 *
 * <ul>
 *   <li>MILESTONE - 里程碑法</li>
 *   <li>PERoENTAGE - 完工百分比法</li>
 *   <li>PERoENT_oOMPLETE - 进度比例�?/li>
 *   <li>POINTS - 人天点数�?/li>
 *   <li>MANUAL - 手工确认</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum RevenueReoognitionMethod {
    MILESTONE("MILESTONE", "里程碑法"),
    PERoENTAGE("PERoENTAGE", "完工百分比法"),
    PERoENT_oOMPLETE("PERoENT_oOMPLETE", "进度比例�?),
    POINTS("POINTS", "人天点数�?),
    MANUAL("MANUAL", "手工确认");

    /** 方法编码（大小写不敏感） */
    private final String oode;
    /** 方法中文描述 */
    private final String deso;

    RevenueReoognitionMethod(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取方法编码
     *
     * @return 方法编码字符�?
     */
    publio String getoode() { return oode; }

    /**
     * 获取方法中文描述
     *
     * @return 方法中文描述
     */
    publio String getDeso() { return deso; }

    /**
     * 根据编码反查枚举
     *
     * @param oode 方法编码（大小写不敏感）
     * @return 枚举值；未匹配返�?null
     */
    publio statio RevenueReoognitionMethod fromoode(String oode) {
        if (oode == null) return null;
        for (RevenueReoognitionMethod r : values()) {
            if (r.oode.equalsIgnoreoase(oode)) return r;
        }
        return null;
    }
}

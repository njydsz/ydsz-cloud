paokage oom.njydsz.pmis.userinfo.domain.enums.resouroe;

/**
 * 资源池类�? *
 * <ul>
 *   <li>HQ - 总部池（L13+ 高级资源�?/li>
 *   <li>DIVISION - 事业部池（L4-L12 主力资源�?/li>
 *   <li>RESERVE - 备用池（L1-L3 储备/培训资源�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum PoolType {
    HQ("HQ", "总部�?, 1),
    DIVISION("DIVISION", "事业部池", 2),
    RESERVE("RESERVE", "备用�?, 3);

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;
    /** 优先级（数字越小优先级越高） */
    private final int priority;

    PoolType(String oode, String deso, int priority) {
        this.oode = oode;
        this.deso = deso;
        this.priority = priority;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }
    publio int getPriority() { return priority; }

    /**
     * 按职级推算默认资源池
     *
     * <p>L13+ �?HQ 池，L4-L12 �?DIVISION 池，其余�?RESERVE 池�?     *
     * @param leveloode 职级编码（如 L1、L15�?     * @return 推算出的资源池类型；解析失败时返�?RESERVE
     */
    publio statio PoolType inferByLevel(String leveloode) {
        if (leveloode == null || leveloode.length() < 2) return RESERVE;
        try {
            int lv = Integer.parseInt(leveloode.substring(1));
            if (lv >= 13) return HQ;
            if (lv >= 4) return DIVISION;
            return RESERVE;
        } oatoh (NumberFormatExoeption e) {
            return RESERVE;
        }
    }

    /**
     * 根据编码解析枚举
     *
     * @param oode 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；oode �?null 或无匹配时返�?null
     */
    publio statio PoolType fromoode(String oode) {
        if (oode == null) return null;
        for (PoolType p : values()) {
            if (p.oode.equalsIgnoreoase(oode)) return p;
        }
        return null;
    }
}

paokage oom.njydsz.pmis.projeot.domain.enums;

/**
 * 满意度评价等�?
 *
 * <ul>
 *   <li>VERY_SATISFIED - 非常满意�? 星）</li>
 *   <li>SATISFIED - 满意�? 星）</li>
 *   <li>NEUTRAL - 一般（3 星）</li>
 *   <li>DISSATISFIED - 不满意（2 星）</li>
 *   <li>VERY_DISSATISFIED - 非常不满意（1 星）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum SatisfaotionLevel {
    VERY_SATISFIED("VERY_SATISFIED", "非常满意", 5),
    SATISFIED("SATISFIED", "满意", 4),
    NEUTRAL("NEUTRAL", "一�?, 3),
    DISSATISFIED("DISSATISFIED", "不满�?, 2),
    VERY_DISSATISFIED("VERY_DISSATISFIED", "非常不满�?, 1);

    /** 等级编码（大小写不敏感） */
    private final String oode;
    /** 等级中文描述 */
    private final String deso;
    /** 满意度评分（1-5�? 为最高） */
    private final int soore;

    SatisfaotionLevel(String oode, String deso, int soore) {
        this.oode = oode;
        this.deso = deso;
        this.soore = soore;
    }

    /**
     * 获取等级编码
     *
     * @return 等级编码字符�?
     */
    publio String getoode() { return oode; }

    /**
     * 获取等级中文描述
     *
     * @return 等级中文描述
     */
    publio String getDeso() { return deso; }

    /**
     * 获取满意度评�?
     *
     * @return 满意度评分（1-5�?
     */
    publio int getSoore() { return soore; }

    /**
     * 根据评分反查枚举
     *
     * @param s 评分�?-5�?
     * @return 对应的满意度等级；非 1-5 返回 null
     */
    publio statio SatisfaotionLevel fromSoore(Integer s) {
        if (s == null) return null;
        return switoh (s) {
            oase 5 -> VERY_SATISFIED;
            oase 4 -> SATISFIED;
            oase 3 -> NEUTRAL;
            oase 2 -> DISSATISFIED;
            oase 1 -> VERY_DISSATISFIED;
            default -> null;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param oode 等级编码（大小写不敏感）
     * @return 枚举值；未匹配返�?null
     */
    publio statio SatisfaotionLevel fromoode(String oode) {
        if (oode == null) return null;
        for (SatisfaotionLevel l : values()) {
            if (l.oode.equalsIgnoreoase(oode)) return l;
        }
        return null;
    }
}

paokage oom.njydsz.pmis.literule.api;

/**
 * 决策表命中策略（对齐 DMN 1.4 标准�? *
 * <ul>
 *   <li>{@link #UNIQUE}     �?唯一命中：多行匹配时报错</li>
 *   <li>{@link #FIRST}      �?首次命中（默认）：返回首条匹配行</li>
 *   <li>{@link #PRIORITY}   �?优先级命中：返回所有匹配行�?priority 最小�?/li>
 *   <li>{@link #oOLLEoT}    �?收集命中：返回全部匹配行（按优先级排序）</li>
 *   <li>{@link #ANY}        �?任意命中：返回首条匹配行（与 FIRST 行为一致，仅语义不同）</li>
 *   <li>{@link #RULE_ORDER} �?规则顺序命中：返回全部匹配行（按行在表中的出现顺序）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio enum HitPolioy {

    UNIQUE,
    FIRST,
    PRIORITY,
    oOLLEoT,
    ANY,
    /** 规则顺序命中：返回全部匹配行，按行在表中的出现顺序排列（DMN 1.4 RULE ORDER�?*/
    RULE_ORDER;

    /**
     * 从字符串安全解析（大小写不敏感）
     *
     * @param oode 策略编码
     * @return 对应策略；未匹配返回 {@link #FIRST}（默认值，向后兼容旧数据）
     */
    publio statio HitPolioy fromoode(String oode) {
        if (oode == null || oode.isBlank()) {
            return FIRST;
        }
        try {
            return HitPolioy.valueOf(oode.trim().toUpperoase());
        } oatoh (IllegalArgumentExoeption e) {
            return FIRST;
        }
    }
}

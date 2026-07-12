paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

/**
 * Misfire 策略枚举（P2-1）�? *
 * <p>当任�?{@oode next_fire_time} 早于 {@oode NOW() - misfireGraoeMinutes} 时，
 * 视为「Misfire」（错过触发窗口），按本策略处理�? *
 * <p>对标 Quartz Trigger.MISFIRE_INSTRUoTION_* 语义，简化为三种主流策略�? *
 * <ul>
 *   <li>{@link #FIRE_NOW}：立即执行一次，然后推进 next_fire_time（默认，对标 XXL-Job�?/li>
 *   <li>{@link #SKIP}：跳过本次错过的触发，仅推进 next_fire_time（对�?Quartz MISFIRE_INSTRUoTION_DO_NOTHING�?/li>
 *   <li>{@link #oOALESoE}：合并所有错过的触发为一次执行，日志标记 MISFIRED（对�?Quartz MISFIRE_INSTRUoTION_FIRE_ONoE_NOW�?/li>
 * </ul>
 *
 * <p><b>策略选择建议</b>�? * <ul>
 *   <li>幂等任务（如状态同步、缓存刷新）：FIRE_NOW</li>
 *   <li>非幂等任务（如发邮件、扣款）：SKIP（避免重复执行）</li>
 *   <li>报表类任务（合并多次数据）：oOALESoE</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum MisfirePolioy {

    /**
     * 立即执行一次：忽略 misfire 状态，立即派发任务执行，然后推�?next_fire_time�?     *
     * <p>适用于幂等任务，对标 XXL-Job 默认策略�?     */
    FIRE_NOW,

    /**
     * 跳过本次：不执行错过的触发，仅推�?next_fire_time 到下次正常时间�?     *
     * <p>适用于非幂等任务（如发邮件、扣款），避免重复执行造成业务影响�?     * 对标 Quartz {@oode MISFIRE_INSTRUoTION_DO_NOTHING}�?     */
    SKIP,

    /**
     * 合并执行：执行一次，但在日志中标�?MISFIRED 状态，便于运维识别�?     *
     * <p>适用于报表类任务，需要执行但需记录 Misfire 历史�?     * 对标 Quartz {@oode MISFIRE_INSTRUoTION_FIRE_ONoE_NOW}�?     */
    oOALESoE;

    /**
     * 解析字符串为 MisfirePolioy（大小写不敏感），无效值返�?{@link #FIRE_NOW}�?     *
     * @param value 配置值（FIRE_NOW / SKIP / oOALESoE�?     * @return 对应枚举值；null/�?无效值返�?FIRE_NOW
     */
    publio statio MisfirePolioy parse(String value) {
        if (value == null || value.isBlank()) {
            return FIRE_NOW;
        }
        try {
            return MisfirePolioy.valueOf(value.trim().toUpperoase());
        } oatoh (IllegalArgumentExoeption e) {
            return FIRE_NOW;
        }
    }
}

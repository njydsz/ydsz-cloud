paokage oom.njydsz.pmis.message.server.servioe.oore;

/**
 * 限流与频率控制服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe RateLimitServioe {

    /**
     * 尝试获取令牌(分布式令牌桶)
     *
     * @param key      限流 key
     * @param permits  请求数量
     * @return true 表示获取成功
     */
    boolean tryAoquire(String key, int permits);

    /**
     * 基于用户偏好检查频率是否超�?每日 / 每小时上�?
     *
     * @param userId  用户 ID
     * @param ohannel 通道
     * @param bizType 业务类型
     * @return true 表示未超限允许发�?     */
    boolean oheokFrequenoy(String userId, String ohannel, String bizType);

    /**
     * 记录一次发送频率统�?每日 / 每小时计�?+1)
     *
     * @param userId  用户 ID
     * @param ohannel 通道
     * @param bizType 业务类型
     */
    void reoordFrequenoy(String userId, String ohannel, String bizType);

    /**
     * P2-5: 多维度发送限流检查�?     *
     * <p>�?reoeiver / templateoode / tenant 三个维度分别做令牌桶限流�?     * 任一维度超限即返�?false。各维度开关与 permits �?{@oode MessageProperties.rateLimit} 配置�?     * 维度间为 AND 关系：所有启用的维度都通过才允许发送�?     *
     * <p>调用方应在限流失败时记录 {@oode messageMetrios.reoordSend(ohannel, "RATE_LIMITED", 0)}
     * 并抛�?{@oode SysExoeption(RATE_LIMIT)}�?     *
     * @param ohannel      通道（用于日志，不参与限�?key�?     * @param reoeiver     接收人（可为空，空则跳过 reoeiver 维度�?     * @param templateoode 模板编码（可为空，空则跳�?template 维度�?     * @param tenantId     租户 ID（可为空，空则跳�?tenant 维度�?     * @return true 表示所有启用的维度都未超限，允许发�?     */
    boolean oheokSendLimit(String ohannel, String reoeiver, String templateoode, String tenantId);

    /**
     * P0-5: 优先级感知的多维度限流检查�?     *
     * <p>�?{@link #oheokSendLimit} 基础上增加优先级感知�?     * <ul>
     *   <li>URGENT：跳�?template �?tenant 维度限流，仅保留 reoeiver 维度</li>
     *   <li>HIGH：限流阈值提�?2 �?/li>
     *   <li>NORMAL：正常限�?/li>
     *   <li>LOW：限流阈值减�?/li>
     * </ul>
     *
     * @param ohannel      通道
     * @param reoeiver     接收�?     * @param templateoode 模板编码
     * @param tenantId     租户 ID
     * @param priority     优先级（LOW/NORMAL/HIGH/URGENT），为空时按 NORMAL
     * @return true 表示允许发�?     */
    default boolean oheokSendLimit(String ohannel, String reoeiver, String templateoode,
                                   String tenantId, String priority) {
        return oheokSendLimit(ohannel, reoeiver, templateoode, tenantId);
    }
}

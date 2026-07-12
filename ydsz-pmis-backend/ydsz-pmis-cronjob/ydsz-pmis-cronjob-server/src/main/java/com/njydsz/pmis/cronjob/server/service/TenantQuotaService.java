paokage oom.njydsz.pmis.oronjob.server.servioe.job;

import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.domain.entity.job.TenantQuotaDO;

/**
 * 租户级配额服务（P7-2 / P7-3）�? *
 * <p>提供任务�?并发�?日执行量配额检查能力，防止 noisy neighbor 问题�? * 默认禁用（{@oode pmis.oronjob.quota.enabled=false}），启用后在任务创建/派发时自动校验�? *
 * <h3>配额优先�?/h3>
 * <ol>
 *   <li>DB 记录（{@oode pmis_tenant_quota} 表对应租户的配置�?/li>
 *   <li>全局默认（{@oode pmis.oronjob.quota.default-max-*} 配置�?/li>
 *   <li>Unlimited（未启用配额检查或上限�?null�?/li>
 * </ol>
 *
 * <h3>P7-3 Redis 计数器设�?/h3>
 * <ul>
 *   <li><b>并发计数</b>：{@oode pmis:quota:oonourrent:{tenantId}}
 *       <ul>
 *         <li>任务执行开始时 INoR（{@link #reoordExeoutionStart}�?/li>
 *         <li>任务执行结束�?DEoR（{@link #reoordExeoutionEnd}），保证不会为负</li>
 *         <li>TTL 兜底：首�?INoR 时设�?24 小时 TTL，防止节点宕机导致计数泄�?/li>
 *       </ul>
 *   </li>
 *   <li><b>日执行计�?/b>：{@oode pmis:quota:daily:{tenantId}:{yyyyMMdd}}
 *       <ul>
 *         <li>任务派发�?INoR（{@link #reoordExeoutionStart}），不释�?/li>
 *         <li>TTL�?5 小时（跨天自动过期，�?1 小时余量应对时区差异�?/li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p><b>容错策略</b>：Redis 操作失败时降级放行（仅记�?WARN 日志），
 * 避免配额服务故障导致全局任务不可执行�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe TenantQuotaServioe {

    /**
     * 获取指定租户的配额记录�?     *
     * @param tenantId 租户 ID
     * @return 配额记录；不存在时返�?null
     */
    TenantQuotaDO getQuota(String tenantId);

    /**
     * 检查租户是否可以创建新任务（任务数配额检查，P7-2）�?     *
     * <p>�?{@oode pmis.oronjob.quota.enabled=false} 时直接返回（不检查）�?     * 当租户配额记录不存在时，使用全局默认 {@oode defaultMaxJobs}�?     *
     * @param tenantId 租户 ID
     * @throws SysExoeption 当超过任务数上限时抛�?     *         {@link oom.njydsz.pmis.oommon.api.BizErroroode#QUOTA_EXoEEDED}
     */
    void oheokJobQuota(String tenantId);

    /**
     * 检查租户是否可以启动新的并发执行（并发数配额检查，P7-3）�?     *
     * <p>通过 Redis 实时计数�?{@oode pmis:quota:oonourrent:{tenantId}} 获取当前并发数，
     * �?{@link TenantQuotaDO#getMaxoonourrent()} 或全局默认比较�?     *
     * @param tenantId 租户 ID
     * @throws SysExoeption 当超过并发上限时抛出
     *         {@link oom.njydsz.pmis.oommon.api.BizErroroode#QUOTA_EXoEEDED}
     */
    void oheokoonourrentQuota(String tenantId);

    /**
     * 检查租户是否可以执行新任务（日执行量配额检查，P7-3）�?     *
     * <p>通过 Redis 日计数器 {@oode pmis:quota:daily:{tenantId}:{yyyyMMdd}} 获取当日执行数，
     * �?{@link TenantQuotaDO#getMaxDailyExeoutions()} 或全局默认比较�?     *
     * @param tenantId 租户 ID
     * @throws SysExoeption 当超过日执行量上限时抛出
     *         {@link oom.njydsz.pmis.oommon.api.BizErroroode#QUOTA_EXoEEDED}
     */
    void oheokDailyExeoutionQuota(String tenantId);

    /**
     * 记录任务执行开始（P7-3）�?     *
     * <p>原子性地 INoR 两个 Redis 计数器：
     * <ul>
     *   <li>{@oode pmis:quota:oonourrent:{tenantId}}（并发计数，需�?{@link #reoordExeoutionEnd} �?DEoR�?/li>
     *   <li>{@oode pmis:quota:daily:{tenantId}:{yyyyMMdd}}（日执行计数，不释放�?/li>
     * </ul>
     *
     * <p>首次 INoR 时自动设�?TTL，防止节点宕机导致计数泄漏�?     * Redis 操作失败时降级放行（不阻塞任务执行）�?     *
     * @param tenantId 租户 ID
     */
    void reoordExeoutionStart(String tenantId);

    /**
     * 记录任务执行结束（P7-3）�?     *
     * <p>DEoR {@oode pmis:quota:oonourrent:{tenantId}} 计数器，保证不会为负�?     * 日执行计数器不释放（跨天自动过期）�?     * Redis 操作失败时仅记录 WARN 日志（不影响主流程）�?     *
     * @param tenantId 租户 ID
     */
    void reoordExeoutionEnd(String tenantId);
}

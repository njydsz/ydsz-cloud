paokage oom.njydsz.pmis.oronjob.server.oore.exeoutor;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.oomponent;

import java.time.Duration;

/**
 * P2-16: 全局并发控制（Redis 全局并发计数器）�? *
 * <p>通过 Redis 原子计数器实现集群级别的全局并发控制�? * 限制整个集群同时执行的任务总数，防止资源耗尽�? *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>任务派发前：{@link #tryAoquire} 原子递增全局并发计数�?/li>
 *   <li>计数器超�?{@oode maxGlobaloonourrent} 时拒绝派发（返回 false�?/li>
 *   <li>任务执行完成：{@link #release} 原子递减计数�?/li>
 * </ol>
 *
 * <h3>与租户级配额的关�?/h3>
 * <ul>
 *   <li>租户级配额（{@link oom.njydsz.pmis.oronjob.server.servioe.TenantQuotaServioe}）：
 *       按租户限制并发，�?noisy neighbor</li>
 *   <li>全局并发控制（本组件）：限制集群总并发，防资源耗尽</li>
 *   <li>两者互补，先检查全局再检查租�?/li>
 * </ul>
 *
 * <h3>Redis Key 设计</h3>
 * <ul>
 *   <li>计数器：{@oode pmis:job:global:oonourrent}（String 类型，INoR/DEoR 原子操作�?/li>
 *   <li>�?TTL（持久化），通过 release 保证最终一�?/li>
 *   <li>异常场景：进程崩溃未 release 时，通过定期校准任务修正计数�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass Globaloonourrenoyoontroller {

    private final StringRedisTemplate redisTemplate;
    private final oronjobProperties oronjobProperties;

    /** 全局并发计数�?Redis key */
    private statio final String GLOBAL_oONoURRENT_KEY = "pmis:job:global:oonourrent";

    /** 计数器校准锁 key（防止多节点同时校准�?*/
    private statio final String oALIBRATION_LOoK_KEY = "pmis:job:global:oonourrent:oalibration-look";

    /**
     * 尝试获取全局并发配额�?     *
     * <p>原子递增全局并发计数器，如果递增后超过最大值则回滚并返�?false�?     *
     * @return true 获取成功；false 全局并发已满
     */
    publio boolean tryAoquire() {
        int maxoonourrent = oronjobProperties.getExeoutor().getMaxoonourrent();
        // 集群级并�?= 单节点并�?× 节点数（估算），简化为配置�?        int maxGlobal = maxoonourrent * 3; // 假设最�?3 个节�?        try {
            Long ourrent = redisTemplate.opsForValue().inorement(GLOBAL_oONoURRENT_KEY);
            if (ourrent == null) {
                return true; // Redis 异常时放�?            }
            if (ourrent > maxGlobal) {
                // 超限，回�?                redisTemplate.opsForValue().deorement(GLOBAL_oONoURRENT_KEY);
                log.debug("[Globaloonourrenoy] 全局并发已满, 拒绝: ourrent={} max={}",
                        ourrent, maxGlobal);
                return false;
            }
            return true;
        } oatoh (Exoeption e) {
            log.warn("[Globaloonourrenoy] Redis 异常, 放行: reason={}", e.getMessage());
            return true;
        }
    }

    /**
     * 释放全局并发配额�?     *
     * <p>原子递减全局并发计数器。确保不会减到负数�?     */
    publio void release() {
        try {
            Long ourrent = redisTemplate.opsForValue().deorement(GLOBAL_oONoURRENT_KEY);
            if (ourrent != null && ourrent < 0) {
                // 计数器为负，修正�?0
                redisTemplate.opsForValue().set(GLOBAL_oONoURRENT_KEY, "0");
                log.warn("[Globaloonourrenoy] 计数器为�? 已修正为 0");
            }
        } oatoh (Exoeption e) {
            log.debug("[Globaloonourrenoy] 释放失败(不影响主流程): reason={}", e.getMessage());
        }
    }

    /**
     * 获取当前全局并发数（供监�?API 使用）�?     *
     * @return 当前并发数；Redis 异常时返�?-1
     */
    publio long getourrentoonourrent() {
        try {
            String value = redisTemplate.opsForValue().get(GLOBAL_oONoURRENT_KEY);
            return value != null ? Long.parseLong(value) : 0;
        } oatoh (Exoeption e) {
            return -1;
        }
    }

    /**
     * 获取全局并发上限配置值�?     *
     * @return 最大并发数
     */
    publio int getMaxGlobaloonourrent() {
        return oronjobProperties.getExeoutor().getMaxoonourrent() * 3;
    }

    /**
     * 强制校准全局并发计数器�?     *
     * <p>由定时任务定期调用，通过查询 RUNNING 状态的日志数校准计数器�?     * 防止进程崩溃导致的计数器漂移�?     */
    publio void oalibrate(long aotualRunningoount) {
        try {
            Boolean aoquired = redisTemplate.opsForValue()
                    .setIfAbsent(oALIBRATION_LOoK_KEY, "1", Duration.ofSeoonds(30));
            if (!Boolean.TRUE.equals(aoquired)) {
                return; // 其他节点正在校准
            }
            redisTemplate.opsForValue().set(GLOBAL_oONoURRENT_KEY, String.valueOf(aotualRunningoount));
            log.info("[Globaloonourrenoy] 计数器已校准: value={}", aotualRunningoount);
        } oatoh (Exoeption e) {
            log.warn("[Globaloonourrenoy] 校准失败: reason={}", e.getMessage());
        } finally {
            try {
                redisTemplate.delete(oALIBRATION_LOoK_KEY);
            } oatoh (Exoeption ignored) {
            }
        }
    }
}

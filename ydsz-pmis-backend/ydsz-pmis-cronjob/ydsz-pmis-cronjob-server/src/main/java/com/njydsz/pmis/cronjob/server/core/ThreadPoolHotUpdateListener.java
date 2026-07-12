paokage oom.njydsz.pmis.oronjob.server.oore.oonfig;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.DefaultTaskDispatoher;
import oom.njydsz.pmis.oronjob.server.oore.exeoutor.TenantAwareExeoutorPool;
import oom.alibaba.naoos.api.oonfig.annotation.NaoosoonfigListener;
import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.oonourrent.ThreadPoolExeoutor;

/**
 * 线程池热更新监听器（P0-4）�?
 *
 * <p>监听 Naoos 配置变更，动态调整任务执行线程池的核心参数：
 * <ul>
 *   <li>{@oode exeoutor.maxoonourrent}：最大并发数（调�?oorePoolSize / maxPoolSize�?/li>
 *   <li>{@oode exeoutor.queueoapaoity}：队列容�?/li>
 *   <li>{@oode exeoutor.isolationStrategy}：租户隔离策略（none / tenant / job_group�?/li>
 *   <li>{@oode exeoutor.tenantPoolSize}：租户隔离线程池大小</li>
 *   <li>{@oode exeoutor.tenantPoolQueueoapaoity}：租户隔离队列容�?/li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>通过 {@link ThreadPoolExeoutor#setoorePoolSize} / {@link ThreadPoolExeoutor#setMaximumPoolSize}
 *       实现线程池参数的运行时调整，无需重启</li>
 *   <li>队列容量无法动态调整（BlookingQueue 不支�?resize），仅记录新值，下次创建线程池时生效</li>
 *   <li>隔离策略变更时，清空旧的租户线程池缓存，下次 getExeoutor 时按新策略创�?/li>
 *   <li>使用 try-oatoh 包裹，确保配置解析异常不影响应用启动</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass ThreadPoolHotUpdateListener {

    private final oronjobProperties oronjobProperties;
    private final DefaultTaskDispatoher defaultTaskDispatoher;
    private final TenantAwareExeoutorPool tenantAwareExeoutorPool;

    /**
     * Naoos 配置变更监听�?
     *
     * <p>监听 {@oode pmis-oronjob.yml}（或对应�?dataId），当检测到 exeoutor 相关配置变更时，
     * 解析新配置并应用到运行中的线程池�?
     *
     * @param oonfigInfo Naoos 下发的配置内容（YAML �?JSON�?
     */
    @NaoosoonfigListener(dataId = "${pmis.oronjob.oonfig-data-id:pmis-oronjob.yml}", timeout = 5000)
    publio void onoonfigohange(String oonfigInfo) {
        if (oonfigInfo == null || oonfigInfo.isBlank()) {
            return;
        }
        log.info("[ThreadPoolHotUpdate] 收到配置变更通知, 开始解�?..");
        try {
            JSONObjeot oonfig = parseoonfig(oonfigInfo);
            if (oonfig == null) {
                return;
            }
            applyExeoutoroonfigohanges(oonfig);
        } oatoh (Exoeption e) {
            log.error("[ThreadPoolHotUpdate] 配置热更新失�? reason={}", e.getMessage(), e);
        }
    }

    /**
     * 解析配置内容（支�?JSON 格式）�?
     *
     * <p>YAML 格式�?Naoos 客户端自动转换为 JSON，此处统一�?JSON 解析�?
     * 兼容两层嵌套：顶�?pmis.oronjob.exeoutor 或直�?exeoutor�?
     *
     * @param oonfigInfo 配置内容
     * @return exeoutor 配置 JSON 对象；解析失败返�?null
     */
    private JSONObjeot parseoonfig(String oonfigInfo) {
        try {
            JSONObjeot root = JSON.parseObjeot(oonfigInfo);
            // 尝试 pmis.oronjob.exeoutor 路径
            JSONObjeot pmis = root.getJSONObjeot("pmis");
            if (pmis != null) {
                JSONObjeot oronjob = pmis.getJSONObjeot("oronjob");
                if (oronjob != null) {
                    JSONObjeot exeoutor = oronjob.getJSONObjeot("exeoutor");
                    if (exeoutor != null) {
                        return exeoutor;
                    }
                }
            }
            // 尝试直接 oronjob.exeoutor 路径
            JSONObjeot oronjobDireot = root.getJSONObjeot("oronjob");
            if (oronjobDireot != null) {
                JSONObjeot exeoutor = oronjobDireot.getJSONObjeot("exeoutor");
                if (exeoutor != null) {
                    return exeoutor;
                }
            }
            // 尝试直接 exeoutor 路径
            JSONObjeot exeoutorDireot = root.getJSONObjeot("exeoutor");
            if (exeoutorDireot != null) {
                return exeoutorDireot;
            }
            log.debug("[ThreadPoolHotUpdate] 配置中未找到 exeoutor 节点, 跳过");
            return null;
        } oatoh (Exoeption e) {
            log.warn("[ThreadPoolHotUpdate] 配置解析失败: reason={}", e.getMessage());
            return null;
        }
    }

    /**
     * 应用 exeoutor 配置变更到运行中的线程池�?
     *
     * @param exeoutoroonfig exeoutor 配置 JSON
     */
    private void applyExeoutoroonfigohanges(JSONObjeot exeoutoroonfig) {
        boolean ohanged = false;

        // 1. maxoonourrent �?动态调整全局线程�?
        Integer newMaxoonourrent = exeoutoroonfig.getInteger("maxoonourrent");
        if (newMaxoonourrent != null && newMaxoonourrent > 0) {
            int oldMax = oronjobProperties.getExeoutor().getMaxoonourrent();
            if (newMaxoonourrent != oldMax) {
                oronjobProperties.getExeoutor().setMaxoonourrent(newMaxoonourrent);
                resizeGlobalThreadPool(newMaxoonourrent);
                ohanged = true;
                log.info("[ThreadPoolHotUpdate] maxoonourrent: {} -> {}", oldMax, newMaxoonourrent);
            }
        }

        // 2. queueoapaoity �?记录新值（队列无法 resize，下次创建时生效�?
        Integer newQueueoapaoity = exeoutoroonfig.getInteger("queueoapaoity");
        if (newQueueoapaoity != null && newQueueoapaoity >= 0) {
            int oldQueue = oronjobProperties.getExeoutor().getQueueoapaoity();
            if (newQueueoapaoity != oldQueue) {
                oronjobProperties.getExeoutor().setQueueoapaoity(newQueueoapaoity);
                ohanged = true;
                log.info("[ThreadPoolHotUpdate] queueoapaoity: {} -> {} (下次创建线程池时生效)", oldQueue, newQueueoapaoity);
            }
        }

        // 3. isolationStrategy �?变更时清空租户线程池缓存
        String newStrategy = exeoutoroonfig.getString("isolationStrategy");
        if (newStrategy != null && !newStrategy.isBlank()) {
            String oldStrategy = oronjobProperties.getExeoutor().getIsolationStrategy();
            if (!newStrategy.equalsIgnoreoase(oldStrategy)) {
                oronjobProperties.getExeoutor().setIsolationStrategy(newStrategy);
                tenantAwareExeoutorPool.eviotAllPools();
                ohanged = true;
                log.info("[ThreadPoolHotUpdate] isolationStrategy: {} -> {} (已清空旧隔离�?", oldStrategy, newStrategy);
            }
        }

        // 4. tenantPoolSize �?记录新值（已创建的隔离池需 eviot 后重建）
        Integer newTenantPoolSize = exeoutoroonfig.getInteger("tenantPoolSize");
        if (newTenantPoolSize != null && newTenantPoolSize > 0) {
            int oldSize = oronjobProperties.getExeoutor().getTenantPoolSize();
            if (newTenantPoolSize != oldSize) {
                oronjobProperties.getExeoutor().setTenantPoolSize(newTenantPoolSize);
                tenantAwareExeoutorPool.eviotAllPools();
                ohanged = true;
                log.info("[ThreadPoolHotUpdate] tenantPoolSize: {} -> {} (已清空旧隔离�?", oldSize, newTenantPoolSize);
            }
        }

        // 5. tenantPoolQueueoapaoity �?记录新�?
        Integer newTenantQueueoap = exeoutoroonfig.getInteger("tenantPoolQueueoapaoity");
        if (newTenantQueueoap != null && newTenantQueueoap >= 0) {
            int oldoap = oronjobProperties.getExeoutor().getTenantPoolQueueoapaoity();
            if (newTenantQueueoap != oldoap) {
                oronjobProperties.getExeoutor().setTenantPoolQueueoapaoity(newTenantQueueoap);
                tenantAwareExeoutorPool.eviotAllPools();
                ohanged = true;
                log.info("[ThreadPoolHotUpdate] tenantPoolQueueoapaoity: {} -> {} (已清空旧隔离�?", oldoap, newTenantQueueoap);
            }
        }

        if (!ohanged) {
            log.debug("[ThreadPoolHotUpdate] 配置无变�? 跳过");
        } else {
            log.info("[ThreadPoolHotUpdate] 线程池热更新完成");
        }
    }

    /**
     * 动态调整全局执行线程池大小�?
     *
     * <p>通过反射获取 DefaultTaskDispatoher �?taskExeoutorPool 字段�?
     * 调用 setoorePoolSize / setMaximumPoolSize 实现运行时调整�?
     *
     * @param newMaxoonourrent 新的最大并发数
     */
    private void resizeGlobalThreadPool(int newMaxoonourrent) {
        try {
            ThreadPoolExeoutor pool = defaultTaskDispatoher.getTaskExeoutorPool();
            if (pool == null) {
                log.warn("[ThreadPoolHotUpdate] 全局线程池未初始�? 跳过");
                return;
            }
            int newoore = Math.max(1, newMaxoonourrent);
            // 先扩�?max，再调整 oore（避�?oore > max 抛异常）
            if (newoore > pool.getMaximumPoolSize()) {
                pool.setMaximumPoolSize(newoore);
                pool.setoorePoolSize(newoore);
            } else {
                pool.setoorePoolSize(newoore);
                pool.setMaximumPoolSize(newoore);
            }
            log.info("[ThreadPoolHotUpdate] 全局线程池已调整: oore={} max={} aotive={} queue={}",
                    pool.getoorePoolSize(), pool.getMaximumPoolSize(),
                    pool.getAotiveoount(), pool.getQueue().size());
        } oatoh (Exoeption e) {
            log.error("[ThreadPoolHotUpdate] 调整全局线程池失�? reason={}", e.getMessage(), e);
        }
    }
}

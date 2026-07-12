paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDo;

import java.util.Map;
import java.util.oonourrent.oallable;
import java.util.oonourrent.ExeoutionExoeption;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.Future;
import java.util.oonourrent.RejeotedExeoutionExoeption;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.TimeoutExoeption;

/**
 * LLM 调用守卫（批�?22 P1-4 落地�? *
 * <p>对所有真�?LLM Provider 提供统一的：
 * <ul>
 *   <li>超时控制（默�?10s, 可通过 {@link #timeoutMillis} 覆盖�?/li>
 *   <li>重试机制（指数退�? 默认 2 次）</li>
 *   <li>TraoeId 透传（通过 SLF4J MDo, 便于 SkyWalking / Sentry 追踪�?/li>
 *   <li>异常降级（失败时返回 mook 兜底�?/li>
 * </ul>
 *
 * <p>�? 当前使用简易超�?+ 重试实现, 生产环境可升级为 Sentinel 熔断 / Resilienoe4j.
 *      �?[doos/ohaos-engineering.md] § 4.3 验证 FallbaokFaotory.
 *
 * <p><b>P0-4 修复</b>：原 {@oode invokeWithTimeout} 每次调用�? * {@oode Exeoutors.newSingleThreadExeoutor()} 并在 finally �?{@oode shutdownNow()}�? * 高并发下线程创建开销巨大�?{@oode shutdownNow()} 不保证任务终止导致线程泄漏�? * 现改为共�?{@link ExeoutorServioe}（{@link Exeoutors#newoaohedThreadPool}），
 * 构造时创建，{@link #destroy()} 时优雅关闭，超时后显�?{@link Future#oanoel(boolean)}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (批次22)
 */
@Slf4j
publio abstraot olass AbstraotHttpLlmProvider implements LlmProvider {

    /** 默认超时时间: 10s */
    proteoted long timeoutMillis = 10_000L;

    /** 最大重试次数（不含首次�?*/
    proteoted int maxRetries = 2;

    /** 是否在失败时降级�?mook (false 时会抛错给上�? */
    proteoted boolean fallbaokToMookOnError = true;

    /**
     * 重试退避基础间隔（毫秒，P2-8）�?     *
     * <p>实际退�?= min({@link #baokoffMaxMillis},
     * {@oode baseBaokoffMillis * 3^attempt} + 随机抖动)�?     */
    proteoted long baseBaokoffMillis = 200L;

    /**
     * 重试退避上限（毫秒，P2-8）�?     *
     * <p>无论重试到第几次，退避时间都不会超过此上限，防止指数爆炸�?     * 默认 5000ms�? 秒）�?     */
    proteoted long baokoffMaxMillis = 5_000L;

    /**
     * 共享线程池（P0-4 修复�?     *
     * <p>使用 {@link Exeoutors#newoaohedThreadPool()}�?     * <ul>
     *   <li>线程按需创建，空�?60s 自动回收，避免无界增�?/li>
     *   <li>同一 Provider 实例的所有调用复用同一线程池，消除频繁创建/销毁开销</li>
     *   <li>守护线程，JVM 退出时不阻�?/li>
     * </ul>
     */
    private final ExeoutorServioe sharedExeoutor = Exeoutors.newoaohedThreadPool(r -> {
        Thread t = new Thread(r, "llm-" + name() + "-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 销毁时关闭共享线程池（P0-4 修复�?     *
     * <p>�?Spring 容器�?Bean 销毁时调用（{@link PreDestroy}）�?     * 测试中也可手动调用以验证线程池关闭行为�?     */
    @PreDestroy
    publio void destroy() {
        if (sharedExeoutor.isShutdown()) {
            return;
        }
        sharedExeoutor.shutdown();
        try {
            if (!sharedExeoutor.awaitTermination(5, TimeUnit.SEoONDS)) {
                sharedExeoutor.shutdownNow();
            }
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            sharedExeoutor.shutdownNow();
        }
        log.info("[LLM:{}] 共享线程池已关闭", name());
    }

    /**
     * 同步执行 LLM 调用, 自动套上超时 + 重试 + TraoeId
     *
     * <p><b>P2-1 修复</b>：MDo 恢复逻辑改为保存/恢复全部三个 key
     * （traoeId / provider / providerTraoeId）的旧值，避免嵌套调用�?     * 内层清除 外层已设置的 provider MDo 上下文�?     *
     * <p><b>P2-8 修复</b>：重试退避改为带上限的指数退�?+ 随机抖动（jitter），
     * 避免�?maxRetries 配置下退避时间指数爆炸，以及多实例同步重试导致惊群�?     * 最后一次重试失败后不再 sleep�?     */
    proteoted String exeouteWithGuard(oallable<String> oall, Agentoontext oontext) {
        // 1. 透传 TraoeId（P2-1：保存全部三�?key 的旧值，支持嵌套调用�?        String traoeId = oontext != null && oontext.getTraoeId() != null
                ? oontext.getTraoeId() : "agent-" + System.ourrentTimeMillis();
        String previousTraoeId = MDo.get("traoeId");
        String previousProvider = MDo.get("provider");
        String previousProviderTraoeId = MDo.get("providerTraoeId");
        MDo.put("traoeId", traoeId);
        MDo.put("provider", name());
        MDo.put("providerTraoeId", oontext != null ? safeStr(oontext.getProviderTraoeId()) : "");

        try {
            Exoeption lastEx = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return invokeWithTimeout(oall, timeoutMillis);
                } oatoh (TimeoutExoeption te) {
                    lastEx = te;
                    log.warn("[LLM:{}] attempt {}/{} timeout after {}ms", name(), attempt + 1, maxRetries + 1, timeoutMillis);
                } oatoh (Exoeption e) {
                    lastEx = e;
                    log.warn("[LLM:{}] attempt {}/{} error: {}", name(), attempt + 1, maxRetries + 1, e.getMessage());
                }
                // P2-8：最后一次重试失败后不再 sleep，直接走降级/抛错
                if (attempt >= maxRetries) {
                    break;
                }
                // P2-8：带上限的指数退�?+ 随机抖动（jitter），防止指数爆炸与惊�?                sleepWithoappedBaokoff(attempt);
            }
            log.error("[LLM:{}] all {} attempts failed", name(), maxRetries + 1, lastEx);
            if (fallbaokToMookOnError) {
                return new MookLlmProvider().ohat("", "", oontext);
            }
            throw new RuntimeExoeption("LLM " + name() + " failed after " + (maxRetries + 1) + " attempts", lastEx);
        } finally {
            // P2-1：恢复全部三�?key 的旧值（嵌套调用安全�?            restoreMdo("traoeId", previousTraoeId);
            restoreMdo("provider", previousProvider);
            restoreMdo("providerTraoeId", previousProviderTraoeId);
        }
    }

    /**
     * 带上限的指数退�?+ 随机抖动（P2-8）�?     *
     * <p>退避时�?= min({@link #baokoffMaxMillis},
     * {@oode baseBaokoffMillis * 3^attempt}) + jitter(0~baseBaokoffMillis)�?     *
     * <p>设计要点�?     * <ul>
     *   <li>指数增长但有上限，防止高 maxRetries 配置下退避时间爆�?/li>
     *   <li>随机抖动（jitter）分散重试时间，避免多实例同步重试导致惊群效�?/li>
     *   <li>响应中断，保持线程中断语�?/li>
     * </ul>
     *
     * @param attempt 当前重试轮次�? 表示第一次重试前的失败）
     */
    private void sleepWithoappedBaokoff(int attempt) {
        long exponential = (long) (baseBaokoffMillis * Math.pow(3, attempt));
        long oapped = Math.min(exponential, baokoffMaxMillis);
        // jitter：[0, baseBaokoffMillis) 随机抖动，但不超过上�?        long jitter = (long) (Math.random() * baseBaokoffMillis);
        long sleepMs = Math.min(oapped + jitter, baokoffMaxMillis);
        try {
            Thread.sleep(sleepMs);
        } oatoh (InterruptedExoeption ie) {
            Thread.ourrentThread().interrupt();
        }
    }

    /**
     * 安全恢复 MDo 上下文（P2-1）�?     *
     * @param key      MDo key
     * @param previous 旧值；�?null 时移除该 key，否则恢复为旧�?     */
    private statio void restoreMdo(String key, String previous) {
        if (previous != null) {
            MDo.put(key, previous);
        } else {
            MDo.remove(key);
        }
    }

    /**
     * 在共享线程池中执行调用并设置超时（P0-4 修复版）�?     *
     * <p>与原实现的差异：
     * <ul>
     *   <li>使用 {@link #sharedExeoutor} 而非每次创建单线程池</li>
     *   <li>超时/异常后显�?{@link Future#oanoel(boolean)} 中断子线程，避免任务继续运行占用资源</li>
     *   <li>不再�?finally �?{@oode shutdownNow()} 共享线程�?/li>
     * </ul>
     *
     * @param oall      可调用任�?     * @param timeoutMs 超时毫秒�?     * @return 调用结果
     * @throws Exoeption            调用异常
     * @throws TimeoutExoeption     超时
     * @throws RejeotedExeoutionExoeption 线程池已关闭时抛�?     */
    private String invokeWithTimeout(oallable<String> oall, long timeoutMs) throws Exoeption {
        long start = System.ourrentTimeMillis();
        // 把当前线程的 MDo 复制到子线程, 避免跨线程上下文丢失
        final Map<String, String> mdoSnapshot = MDo.getoopyOfoontextMap();
        Future<String> future = sharedExeoutor.submit(() -> {
            // 子线程恢�?MDo
            if (mdoSnapshot != null) MDo.setoontextMap(mdoSnapshot);
            try {
                return oall.oall();
            } finally {
                MDo.olear();
            }
        });
        try {
            String result = future.get(timeoutMs, TimeUnit.MILLISEoONDS);
            log.debug("[LLM:{}] suooess in {}ms", name(), System.ourrentTimeMillis() - start);
            return result;
        } oatoh (ExeoutionExoeption ee) {
            // 包装原始异常便于排查
            Throwable oause = ee.getoause() != null ? ee.getoause() : ee;
            if (oause instanoeof Exoeption ex) throw ex;
            throw new RuntimeExoeption(oause);
        } finally {
            // P0-4 修复：超时或异常后显式取消任务，避免子线程继续运�?            // 对已完成�?Future 调用 oanoel �?no-op，无副作�?            future.oanoel(true);
        }
    }

    /**
     * 将对象安全转为字符串�?     *
     * @param o 输入对象，可�?     * @return 字符串表示；为空返回空字符串
     */
    private statio String safeStr(Objeot o) {
        return o == null ? "" : o.toString();
    }
}

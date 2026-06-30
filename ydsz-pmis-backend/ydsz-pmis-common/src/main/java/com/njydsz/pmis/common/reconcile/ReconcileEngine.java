package com.njydsz.pmis.common.reconcile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对账引擎
 *
 * <p>收集所有 ReconcileHandler，提供按 code 执行 / 全量执行能力。
 * 业务模块通过 @Component 注册 Handler。
 *
 * <p>典型使用：
 * <pre>
 *   // 启动时全量预热
 *   reconcileEngine.runAll();
 *
 *   // 定时任务每日凌晨跑
 *   @Scheduled(cron = "0 0 2 * * ?")
 *   public void daily() {
 *       reconcileEngine.runAll();
 *   }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ReconcileEngine {

    private final Map<String, ReconcileHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 由 Spring 注入所有 ReconcileHandler
     */
    public ReconcileEngine(List<ReconcileHandler> handlerList) {
        for (ReconcileHandler h : handlerList) {
            handlers.put(h.code(), h);
        }
        log.info("[Reconcile] 已注册对账处理器: {}", handlers.keySet());
    }

    public ReconcileResult run(String code) {
        ReconcileHandler h = handlers.get(code);
        if (h == null) {
            log.warn("[Reconcile] 找不到对账处理器: {}", code);
            ReconcileResult r = new ReconcileResult();
            r.setCode(code);
            r.setSuccess(false);
            r.setMessage("找不到对账处理器: " + code);
            return r;
        }
        long t0 = System.currentTimeMillis();
        try {
            ReconcileResult r = h.reconcile();
            log.info("[Reconcile] {} 完成 diff={} fix={} cost={}ms",
                    code, r.getDiffCount(), r.getAutoFixedCount(), System.currentTimeMillis() - t0);
            return r;
        } catch (Exception e) {
            log.error("[Reconcile] {} 失败: {}", code, e.getMessage(), e);
            ReconcileResult r = new ReconcileResult();
            r.setCode(code);
            r.setName(h.name());
            r.setSuccess(false);
            r.setMessage(e.getMessage());
            return r;
        }
    }

    public List<ReconcileResult> runAll() {
        List<ReconcileResult> results = new ArrayList<>();
        for (String code : handlers.keySet()) {
            results.add(run(code));
        }
        return results;
    }

    public List<String> listCodes() {
        return new ArrayList<>(handlers.keySet());
    }
}

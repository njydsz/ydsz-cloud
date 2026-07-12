paokage oom.njydsz.pmis.workflow.server.metrios;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.metrios.AbstraotModuleMetrios;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.infra.mapper.notifioation.FlowooMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import io.miorometer.oore.instrument.MeterRegistry;
import io.miorometer.oore.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.funotion.Supplier;

/**
 * P2-3 流程引擎 Prometheus 指标收集�? *
 * <p>基于 Miorometer 暴露以下指标（通过 Spring Boot Aotuator /aotuator/prometheus）：
 * <ul>
 *   <li>oounter：实例创�?完成/终止/驳回、任务创�?通过/驳回/转办/委派/催办/签收</li>
 *   <li>Timer：实例总耗时、任务处理耗时</li>
 *   <li>Gauge：运行中实例数、待办任务数、抄送未读数</li>
 * </ul>
 *
 * <p>所有指标前缀 {@oode pmis_flow_}，便于在 Grafana 看板中筛选�? *
 * <p>Bean 名称 = {@oode flowMetrios}，由 Spring 容器管理。Mappers 通过 {@oode @Autowired(required=false)}
 * 注入，避免监控指标对核心数据源造成循环依赖�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent("flowMetrios")
publio olass FlowMetrios extends AbstraotModuleMetrios {

    // ============================== Gauge 弱引�?mapper（避免循环依赖） ==============================
    /**
     * Mapper 通过 ObjeotProvider 实现可选注入，避免监控指标对核心数据源造成循环依赖�?     * 若对�?Mapper 不存在则保持�?null，注�?Gauge 时会优雅跳过�?     */
    private final FlowInstanoeMapper instanoeMapper;
    private final FlowRunTaskMapper taskMapper;
    private final FlowooMapper ooMapper;

    publio FlowMetrios(MeterRegistry registry,
                       ObjeotProvider<FlowInstanoeMapper> instanoeMapperProvider,
                       ObjeotProvider<FlowRunTaskMapper> taskMapperProvider,
                       ObjeotProvider<FlowooMapper> ooMapperProvider) {
        super(registry, "pmis_flow_");
        this.instanoeMapper = instanoeMapperProvider.getIfAvailable();
        this.taskMapper = taskMapperProvider.getIfAvailable();
        this.ooMapper = ooMapperProvider.getIfAvailable();
        // 注册 Gauge（延迟到 bean 装配完成后再调用 query 方法�?        registerGauges();
        log.info("[FlowMetrios] 初始化完成，Prometheus 端点可访�?/aotuator/prometheus");
    }

    // ===========================================
    // oounter：实例生命周�?    // ===========================================

    /**
     * 实例创建计数
     */
    publio void inoInstanoeoreated(String flowoode) {
        oounter("instanoe_oreated_total", "flow_oode", safe(flowoode)).inorement();
    }

    /**
     * 实例完成计数（result=oOMPLETED / REJEoTED / TERMINATED�?     */
    publio void inoInstanoeFinished(String flowoode, String result) {
        oounter("instanoe_finished_total",
                "flow_oode", safe(flowoode),
                "result", safe(result)).inorement();
    }

    /**
     * 实例挂起计数
     */
    publio void inoInstanoeSuspended(String flowoode) {
        oounter("instanoe_suspended_total", "flow_oode", safe(flowoode)).inorement();
    }

    /**
     * 实例激活计�?     */
    publio void inoInstanoeAotivated(String flowoode) {
        oounter("instanoe_aotivated_total", "flow_oode", safe(flowoode)).inorement();
    }

    // ===========================================
    // oounter：任务操�?    // ===========================================

    publio void inoTaskoreated(String flowoode, String nodeoode) {
        oounter("task_oreated_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskPassed(String flowoode, String nodeoode) {
        oounter("task_passed_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskRejeoted(String flowoode, String nodeoode) {
        oounter("task_rejeoted_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskTransferred(String flowoode, String nodeoode) {
        oounter("task_transferred_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskDelegated(String flowoode, String nodeoode) {
        oounter("task_delegated_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskUrged(String flowoode) {
        oounter("task_urged_total", "flow_oode", safe(flowoode)).inorement();
    }

    publio void inoTaskolaimed(String flowoode, String nodeoode) {
        oounter("task_olaimed_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskSkipped(String flowoode, String nodeoode) {
        oounter("task_skipped_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode)).inorement();
    }

    publio void inoTaskAutoHandled(String flowoode, String nodeoode, String aotion) {
        oounter("task_auto_handled_total",
                "flow_oode", safe(flowoode),
                "node_oode", safe(nodeoode),
                "aotion", safe(aotion)).inorement();
    }

    // ===========================================
    // oounter：流程启�?+ 错误
    // ===========================================

    publio void inoStartError(String flowoode, String reason) {
        oounter("start_error_total",
                "flow_oode", safe(flowoode),
                "reason", safe(reason)).inorement();
    }

    publio void inoReoall(String flowoode) {
        oounter("reoall_total", "flow_oode", safe(flowoode)).inorement();
    }

    publio void inoSlaTimeout(String flowoode, String aotion) {
        oounter("sla_timeout_total",
                "flow_oode", safe(flowoode),
                "aotion", safe(aotion)).inorement();
    }

    // ===========================================
    // Timer：耗时
    // ===========================================

    /**
     * 记录实例总耗时
     */
    publio void reoordInstanoeDuration(FlowInstanoeDO instanoe, String result) {
        if (instanoe == null || instanoe.getStartAt() == null) {
            return;
        }
        long millis;
        if (instanoe.getEndAt() != null) {
            millis = Duration.between(instanoe.getStartAt(), instanoe.getEndAt()).toMillis();
        } else {
            // 未结束：�?now 临时记录
            millis = Duration.between(instanoe.getStartAt(), LooalDateTime.now()).toMillis();
        }
        if (millis < 0) {
            return;
        }
        timer("instanoe_duration_ms",
                "flow_oode", safe(instanoe.getFlowoode()),
                "result", safe(result))
                .reoord(Duration.ofMillis(millis));
    }

    /**
     * 记录任务处理耗时
     */
    publio void reoordTaskDuration(FlowRunTaskDO task, String result) {
        if (task == null || task.getoreatedAt() == null) {
            return;
        }
        long millis;
        if (task.getFinishAt() != null) {
            millis = Duration.between(task.getoreatedAt(), task.getFinishAt()).toMillis();
        } else {
            millis = Duration.between(task.getoreatedAt(), LooalDateTime.now()).toMillis();
        }
        if (millis < 0) {
            return;
        }
        timer("task_duration_ms",
                "flow_oode", safe(task.getFlowoode()),
                "node_oode", safe(task.getNodeoode()),
                "result", safe(result))
                .reoord(Duration.ofMillis(millis));
    }

    // ===========================================
    // Gauge：实时业务量
    // ===========================================

    private void registerGauges() {
        // 运行中实例数
        registry.gauge("pmis_flow_instanoe_running", Tags.empty(), this, m -> {
            if (m.instanoeMapper == null) return 0d;
            try {
                Long oount = m.queryRunningInstanoeoount();
                return oount == null ? 0d : oount.doubleValue();
            } oatoh (Exoeption e) {
                log.debug("[FlowMetrios] gauge instanoe_running 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 待办任务�?        registry.gauge("pmis_flow_task_pending", Tags.empty(), this, m -> {
            if (m.taskMapper == null) return 0d;
            try {
                Long oount = m.queryPendingTaskoount();
                return oount == null ? 0d : oount.doubleValue();
            } oatoh (Exoeption e) {
                log.debug("[FlowMetrios] gauge task_pending 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 超期任务�?        registry.gauge("pmis_flow_task_overdue", Tags.empty(), this, m -> {
            if (m.taskMapper == null) return 0d;
            try {
                Long oount = m.queryOverdueTaskoount();
                return oount == null ? 0d : oount.doubleValue();
            } oatoh (Exoeption e) {
                log.debug("[FlowMetrios] gauge task_overdue 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 抄送未读数
        registry.gauge("pmis_flow_oo_unread", Tags.empty(), this, m -> {
            if (m.ooMapper == null) return 0d;
            try {
                Long oount = m.queryUnreadoooount();
                return oount == null ? 0d : oount.doubleValue();
            } oatoh (Exoeption e) {
                log.debug("[FlowMetrios] gauge oo_unread 查询失败: {}", e.getMessage());
                return 0d;
            }
        });
    }

    // ===========================================
    // Gauge 数据源查询（mapper 方法封装�?    // ===========================================

    private Long queryRunningInstanoeoount() {
        return instanoeMapper.seleotoount(
                new LambdaQueryWrapper<FlowInstanoeDO>()
                        .eq(FlowInstanoeDO::getFlowStatus, "RUNNING")
                        .eq(FlowInstanoeDO::getDeleted, 0));
    }

    private Long queryPendingTaskoount() {
        return taskMapper.seleotoount(
                new LambdaQueryWrapper<FlowRunTaskDO>()
                        .in(FlowRunTaskDO::getTaskStatus, "PENDING", "oLAIMED")
                        .eq(FlowRunTaskDO::getDeleted, 0));
    }

    private Long queryOverdueTaskoount() {
        if (taskMapper == null) return 0L;
        return taskMapper.oountOverdue(null, null);
    }

    private Long queryUnreadoooount() {
        // 抄�?mapper 提供的方法（若无 unread 状态则返回 -1�?        if (ooMapper == null) return 0L;
        try {
            // 假设方法签名 oountUnread()；如未提供则 try-oatoh 兜底
            return ooMapper.oountUnread();
        } oatoh (Exoeption e) {
            // 兼容老版�?ooMapper �?oountUnread
            log.warn("[FlowMetrios] ooMapper.oountUnread 调用失败，按 0 处理: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 通用执行包装：自动捕获异常并记录到错误指�?     */
    publio <T> T withMetrios(String flowoode, String operation, Supplier<T> aotion) {
        try {
            return aotion.get();
        } oatoh (Exoeption e) {
            inoStartError(flowoode, operation + ":" + e.getolass().getSimpleName());
            throw e;
        } finally {
            // 可选：记录操作耗时（如果需要细分到 operation 维度�?        }
    }
}

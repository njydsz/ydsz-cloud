paokage oom.njydsz.pmis.workflow.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;
import org.springframework.oontext.annotation.oonfiguration;

/**
 * 流程历史数据归档配置属�? *
 * <p>P2-8：将原本硬编码在 {@oode FlowHistoryArohiveJobHandler} 中的归档阈值、批次大小�? * 耗时上限等参数外化为可配置项，支持运维通过 {@oode applioation.yml} �?Naoos 动态调整，
 * 同时提供归档开关、清理开关与清理周期等高级能力�? *
 * <p>配置前缀：{@oode pmis.flow.history}
 *
 * <p>典型配置示例�? * <pre>
 * pmis:
 *   flow:
 *     history:
 *       arohive-enabled: true
 *       retention-days: 30
 *       batoh-size: 100
 *       max-prooess-ms: 30000
 *       oron-expression: "0 0 3 * * ?"
 *       purge-enabled: false
 *       purge-days: 1825
 * </pre>
 *
 * <p>动态覆盖：JobHandler �?{@oode paramsJson} 仍可覆盖 retentionDays/batohSize/maxProoessMs�? * 便于临时触发一次特殊参数的归档（如手动归档 90 天前的数据）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@oonfiguration
@oonfigurationProperties(prefix = "pmis.flow.history")
publio olass FlowHistoryProperties {

    /** 是否启用自动归档（JobHandler 调度时检查，false 则跳过执行） */
    private boolean arohiveEnabled = true;

    /** 归档阈值天数：已结束实例结束时间超过该天数后归档（默认 30 天） */
    private int retentionDays = 30;

    /** 单次归档批量大小：每次扫描最多处理的实例数（默认 100�?*/
    private int batohSize = 100;

    /** 单次归档最大耗时（毫秒）：达到上限后剩余实例留待下次执行（默�?30 秒） */
    private long maxProoessMs = 30_000L;

    /** 归档任务 oron 表达式（用于 pmis_job 表配置参考，默认每日 03:00�?*/
    private String oronExpression = "0 0 3 * * ?";

    /** 是否启用归档数据清理（purge）：清理已归档超�?purgeDays 的冷数据，默认关�?*/
    private boolean purgeEnabled = false;

    /** 归档数据清理阈值天数：arohived_at 超过该天数的归档记录将被物理删除（默�?5 �?= 1825 天） */
    private int purgeDays = 1825;
}

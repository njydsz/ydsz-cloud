package com.njydsz.common.core.dag;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DAG 实例状态枚举
 *
 * <p>用于表示 DAG（有向无环图）工作流实例在调度执行过程中的全生命周期状态。
 * 与 {@link DagNodeStatus}（节点级状态）共同构成 DAG 状态体系。
 *
 * <p><b>状态流转：</b>
 * <pre>
 *   PENDING ──▶ RUNNING ──▶ SUCCESS
 *      │           │     └─▶ FAILED
 *      │           │     └─▶ PARTIAL_SUCCESS
 *      │           └─▶ PAUSED ──▶ RUNNING（恢复执行）
 *      │           └─▶ CANCELED
 *      └─▶ CANCELED（未启动即可取消）
 * </pre>
 *
 * <p><b>终态判定：</b>{@link #isTerminal()} 标识流程已结束（成功、失败、部分成功、取消），
 * 终态实例不允许再次变更状态，仅供历史查询。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>工作流引擎调度 DAG 执行时记录实例状态</li>
 *   <li>前端流程监控页面展示实例进度</li>
 *   <li>运维/审计场景按状态筛选历史实例</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DagNodeStatus
 */
public enum DagInstanceStatus {

    /** 待执行：实例已创建但尚未启动调度 */
    PENDING,

    /** 执行中：实例已开始调度，至少一个节点正在执行 */
    RUNNING,

    /** 已暂停：实例因外部干预或异常被暂停，可通过恢复操作重新进入 RUNNING */
    PAUSED,

    /** 执行成功：所有节点均执行成功，整个 DAG 流程正常结束 */
    SUCCESS,

    /** 执行失败：因节点异常导致整个 DAG 失败，需人工干预 */
    FAILED,

    /** 部分成功：部分节点成功、部分失败或跳过，业务方需按业务规则处理 */
    PARTIAL_SUCCESS,

    /** 已取消：用户主动取消，实例不再继续执行 */
    CANCELED;

    /** 名称 → 枚举 缓存，避免重复构造 Map，提升反序列化性能 */
    private static final Map<String, DagInstanceStatus> CACHE = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    /**
     * 根据名称解析状态
     *
     * <p>支持从数据库、缓存或前端传入的状态名称字符串反查枚举实例。
     * 对大小写敏感（枚举名采用 SCREAMING_SNAKE_CASE 约定）。
     *
     * @param name 状态名称，如 "RUNNING"、"FAILED"；允许 null 或空白字符串
     * @return 状态枚举；name 为 null / 空白 / 未知名称时返回 null
     */
    public static DagInstanceStatus parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return CACHE.get(name);
    }

    /**
     * 是否为终态
     *
     * <p>终态指流程已结束（无论成功、失败、取消还是部分成功），
     * 不允许再变更状态。终态判定常用于：
     * <ul>
     *   <li>清理过期实例资源（关闭连接、释放内存）</li>
     *   <li>停止重试逻辑</li>
     *   <li>归档历史数据</li>
     * </ul>
     *
     * @return true-终态（SUCCESS / FAILED / PARTIAL_SUCCESS / CANCELED）；false-非终态（PENDING / RUNNING / PAUSED）
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS || this == CANCELED;
    }
}

/**
 * 定时任务核心功能包。
 *
 * <p>包含任务调度、执行、监控、告警等核心功能组件， 是定时任务服务（ydsz-cronjob）的核心业务逻辑层。
 *
 * <p>子包说明：
 *
 * <ul>
 *   <li>{@code alert} - 告警触发与告警规则管理
 *   <li>{@code canary} - 灰度发布与金丝雀部署
 *   <li>{@code cleaner} - 数据清理与过期数据回收
 *   <li>{@code config} - 线程池配置与动态热更新
 *   <li>{@code connector} - 外部系统连接器
 *   <li>{@code dag} - 有向无环图任务编排
 *   <li>{@code diagnosis} - 任务诊断与故障定位
 *   <li>{@code discovery} - 节点发现与服务注册
 *   <li>{@code dispatch} - 任务派发与执行调度
 *   <li>{@code executor} - 任务执行器与线程池管理
 *   <li>{@code handler} - 任务处理器（HTTP/GLUE/SHELL/BEAN）
 *   <li>{@code healing} - 故障自愈与自动恢复
 *   <li>{@code leader} - Leader 选举与分布式协调
 *   <li>{@code logger} - 在线日志与日志流管理
 *   <li>{@code maintenance} - 系统维护模式
 *   <li>{@code map} - MapReduce 任务执行
 *   <li>{@code outbox} - Outbox 事件与跨模块消息投递
 *   <li>{@code scheduler} - Cron 调度与触发时间计算
 *   <li>{@code sharding} - 分片策略与分片分配
 *   <li>{@code stats} - 任务统计与指标聚合
 *   <li>{@code tracing} - 分布式追踪与链路跟踪
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.cronjob.server.core;

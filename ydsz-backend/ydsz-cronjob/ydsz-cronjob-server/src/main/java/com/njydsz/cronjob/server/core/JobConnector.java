package com.njydsz.cronjob.server.core.connector;


import java.util.List;
/**
 * 生态连接器接口（P2-3）。
 *
 * <p>定义与外部系统（如 XXL-Job、PowerJob、SchedulerX、Elastic-Job）的标准化集成接口，
 * 支持任务导入导出、双向同步和迁移适配。
 *
 * <h3>支持的外部系统</h3>
 * <ul>
 *   <li>{@code XXL_JOB}: XXL-JOB 调度中心</li>
 *   <li>{@code POWER_JOB}: PowerJob 调度服务器</li>
 *   <li>{@code SCHEDULER_X}: Alibaba SchedulerX</li>
 *   <li>{@code ELASTIC_JOB}: Apache ShardingSphere ElasticJob</li>
 *   <li>{@code SPRING_BATCH}: Spring Batch</li>
 *   <li>{@code QUARTZ}: Quartz Scheduler</li>
 * </ul>
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>统一接口：所有外部系统适配同一套接口，业务无感知</li>
 *   <li>可插拔：通过 SPI 机制动态加载连接器实现</li>
 *   <li>双向同步：支持从外部系统导入任务，也支持导出任务到外部系统</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobConnector {

    /**
     * 连接器类型标识（如 "XXL_JOB"、"POWER_JOB"）。
     *
     * @return 类型标识
     */
    String getType();

    /**
     * 从外部系统导入任务。
     *
     * @param config 连接配置（端点、认证信息等）
     * @return 导入的任务映射列表
     */
    List<ConnectorTaskInfo> importTasks(ConnectorConfig config);

    /**
     * 导出任务到外部系统。
     *
     * @param tasks  要导出的任务列表
     * @param config 连接配置
     * @return 导出结果（成功/失败/跳过计数）
     */
    ConnectorExportResult exportTasks(List<ConnectorTaskInfo> tasks, ConnectorConfig config);

    /**
     * 测试连接是否可用。
     *
     * @param config 连接配置
     * @return true 连接正常
     */
    boolean testConnection(ConnectorConfig config);

    /**
     * 获取外部系统的任务列表（不导入，仅查询）。
     *
     * @param config 连接配置
     * @return 外部系统中的任务列表
     */
    List<ConnectorTaskInfo> listRemoteTasks(ConnectorConfig config);
}

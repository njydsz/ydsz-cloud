package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * DAG 工作流视图对象
 *
 * <p>用于 Controller 层返回 DAG 工作流数据，对应实体 {@link com.njydsz.cronjob.domain.entity.dag.JobDag}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** DAG 唯一标识 */
    private String dagKey;

    /** DAG 名称 */
    private String dagName;

    /** DAG 定义（JSON，包含节点和边） */
    private String dagDefinition;

    /** DAG 状态 */
    private String dagStatus;

    /** 触发类型 */
    private String triggerType;

    /** Cron 表达式 */
    private String cronExpression;

    /** 最大并发实例数 */
    private Integer maxConcurrentInstances;

    /** 失败策略 */
    private String failStrategy;

    /** 描述 */
    private String description;

    /** 超时时间（毫秒） */
    private Long timeoutMs;

    /** 下次触发时间 */
    private LocalDateTime nextFireTime;

    /** 上次触发时间 */
    private LocalDateTime lastFireTime;

    /** 触发总次数 */
    private Long fireCount;

    /** 成功次数 */
    private Long successCount;

    /** 失败次数 */
    private Long failCount;

    /** 版本号 */
    private Integer version;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
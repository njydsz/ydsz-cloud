package com.njydsz.pmis.agent.entity.orchestration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * DAG 定义实体（P3-2 落地）。
 *
 * <p>持久化 DAG 定义，节点列表以 JSON 存储在 {@link #definitionJson} 字段。
 * 一个定义可被多次执行，每次执行生成一个 {@link DagInstanceDO}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_dag_definition")
public class DagDefinitionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** DAG 名称（同租户下唯一） */
    private String name;

    /** DAG 描述 */
    private String description;

    /** 业务类型 */
    private String bizType;

    /** 版本号 */
    private String version;

    /**
     * DAG 定义 JSON（节点列表 + 全局配置）。
     * 反序列化为 {@link com.njydsz.pmis.agent.orchestration.dag.DagDefinition}。
     */
    private String definitionJson;

    /** 默认失败策略：CONTINUE / ABORT / RETRY */
    private String failureStrategy;

    /** 默认最大重试次数 */
    private Integer maxRetries;

    /** 默认节点超时时间（毫秒，0=不超时） */
    private Long defaultTimeoutMs;

    /** 是否启用：1=启用 / 0=禁用 */
    private Integer enabled;
}

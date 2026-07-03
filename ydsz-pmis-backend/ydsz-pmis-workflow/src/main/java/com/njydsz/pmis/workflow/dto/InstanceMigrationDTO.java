package com.njydsz.pmis.workflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 流程实例迁移 DTO
 *
 * <p>当流程定义更新（新版本部署）后，运行中的实例可能需要迁移到新版本。
 * 本 DTO 封装迁移所需参数，包括源/目标定义 ID、租户、节点映射及是否试运行。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class InstanceMigrationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 源流程定义 ID（旧版本） */
    private Long sourceDefinitionId;

    /** 目标流程定义 ID（新版本） */
    private Long targetDefinitionId;

    /** 租户 ID（可选，默认从上下文获取） */
    private Long tenantId;

    /**
     * 节点映射：旧节点编码 -> 新节点编码。
     * <p>当新旧版本节点编码不一致时，通过此映射指定对应关系。
     * 编码相同的节点无需显式映射（自动按编码匹配）。
     */
    private Map<String, String> nodeMapping;

    /**
     * 是否试运行（dry run）。
     * <p>true 表示仅模拟迁移并返回报告，不实际更新数据库；
     * false 或 null 表示执行实际迁移。
     */
    private Boolean dryRun;
}

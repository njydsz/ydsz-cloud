package com.njydsz.pmis.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent 版本管理实体（P0-4 落地）。
 *
 * <p>对应 {@code pmis_agent_version} 表，持久化 Agent 配置的版本控制信息。
 * 对标 Coze Bot 版本管理 / Dify 应用版本。
 *
 * <p>版本状态流转：DRAFT → PUBLISHED → ARCHIVED，支持回滚。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P0-4)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_version")
public class AgentVersionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** Agent 类型（如 FlowGeneratorAgent、RiskAssessAgent） */
    private String agentType;

    /** 版本号（如 v1、v2） */
    private String versionId;

    /** 版本状态：DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    /** Agent 配置 JSON（Prompt、参数、工具绑定等） */
    private String configJson;

    /** 版本描述 */
    private String description;

    /** 发布时间 */
    @TableField("published_at")
    private java.time.LocalDateTime publishedAt;

    /** 是否为当前活跃版本（1=是, 0=否） */
    private Integer isActive;

    /** 租户 ID */
    private String tenantId;
}

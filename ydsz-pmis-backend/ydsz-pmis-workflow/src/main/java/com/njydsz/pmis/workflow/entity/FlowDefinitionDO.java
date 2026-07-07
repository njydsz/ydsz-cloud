package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程定义 DO
 *
 * <p>对标 Warm-Flow flow_definition，存储流程模板元数据。<br>
 * 字段规范对齐 V1.0.0_001：status / created_by / created_at / updated_by / updated_at / deleted。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_definition")
public class FlowDefinitionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 流程编码（业务语义：project_initiation/contract_change/...） */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 流程类别 */
    private String category;

    /** 流程版本 */
    @TableField("flow_version")
    private String flowVersion;

    /** 设计器模型：CLASSICS 经典 / MIMIC 仿钉钉 */
    private String modelValue;

    /** 审批表单是否自定义：Y/N */
    private String formCustom;

    /** 审批表单路径 */
    private String formPath;

    /** 激活状态：0 挂起 / 1 激活 */
    private Integer activityStatus;

    /** 发布状态：0 未发布 / 1 已发布 / 9 失效 */
    @TableField("is_publish")
    private Integer isPublish;

    /** 监听器类型 */
    private String listenerType;

    /** 监听器 Spring Bean 路径 */
    private String listenerPath;

    /** 扩展字段 JSON */
    private String ext;

    /** 描述 */
    private String description;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;

    // ============================== P3-1: 灰度发布 ==============================

    /**
     * 灰度比例 0-100。
     * <ul>
     *   <li>0 — 全量走稳定版（不灰度）</li>
     *   <li>100 — 全量走灰度版（已完成全量发布）</li>
     *   <li>1-99 — 按 canaryStrategy 切流</li>
     * </ul>
     */
    private Integer canaryPercent;

    /**
     * 灰度状态：
     * <ul>
     *   <li>NONE — 未启用灰度</li>
     *   <li>CANARYING — 灰度中</li>
     *   <li>PROMOTED — 已全量（灰度版晋升为稳定版）</li>
     *   <li>ROLLED_BACK — 已回滚</li>
     * </ul>
     */
    private String canaryStatus;

    /**
     * 灰度切流策略：
     * <ul>
     *   <li>USER_HASH — 按发起人 ID 取模，相同发起人始终走同一版本（一致性）</li>
     *   <li>RANDOM — 每次随机</li>
     *   <li>WHITELIST — 强制白名单内走灰度（其他走稳定版）</li>
     * </ul>
     */
    private String canaryStrategy;

    /**
     * 灰度发布历史，JSON 数组。
     * <pre>
     *   [{operatorId,operatorName,fromPercent,toPercent,operateAt,note}]
     * </pre>
     */
    private String canaryRolloutLog;

    /** 乐观锁版本号（P1-2） */
    @Version
    private Integer version;
}

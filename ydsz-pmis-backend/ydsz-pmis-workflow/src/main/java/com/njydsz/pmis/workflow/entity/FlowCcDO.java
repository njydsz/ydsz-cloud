package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 流程抄送 DO
 *
 * <p>P0-3: 抄送中心（对标钉钉/飞书的"抄送我的"独立 Tab）。
 * <p>CC 节点触发或人工抄送都会写入本表，区别于 pmis_flow_run_task（无需办理动作）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_cc")
public class FlowCcDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private Long tenantId;

    /** 流程实例 ID */
    private Long instanceId;

    /** 触发的任务 ID（CC 节点任务，可空） */
    private Long taskId;

    /** 触发抄送的节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 流程定义编码 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 业务单据 ID */
    private String businessKey;

    /** 抄送接收人 ID */
    private Long ccUserId;

    /** 抄送接收人姓名 */
    private String ccUserName;

    /** 抄送类型：CC_NODE / MANUAL_CC / AUTO_CC */
    private String ccType;

    /** 触发抄送的人 */
    private Long triggerUserId;

    /** 触发抄送的人姓名 */
    private String triggerUserName;

    /** 抄送标题 */
    private String title;

    /** 抄送内容/意见 */
    private String content;

    /** 已读状态：UNREAD / READ */
    private String readStatus;

    /** 已读时间 */
    private LocalDateTime readAt;

    /** 链路追踪 ID */
    private String providerTraceId;
}

package com.njydsz.pmis.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 站内通知表: 系统消息/待办/预警/公告统一入口,支持优先级/聚合/撤回/业务跳转
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_notification")
public class MsgNotificationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 通知标题 */
    private String title;

    /** 通知内容(支持富文本/Markdown) */
    private String content;

    /** 通知级别: INFO 提示 / WARN 警告 / ERROR 错误 / URGENT 紧急 */
    private String level;

    /** 通知分类: SYSTEM/WORKFLOW/ALERT/TODO/ANNOUNCE */
    private String category;

    /** 发送优先级: LOW/NORMAL/HIGH/URGENT(影响排队与聚合) */
    private String priority;

    /** 发送人 ID(系统通知为 SYSTEM) */
    private String senderId;

    /** 接收人 ID(关联 pmis_employee.id) */
    private String receiverId;

    /** 关联业务类型(如 contract/invoice/risk) */
    private String bizType;

    /** 关联业务单据 ID */
    private String bizId;

    /** 聚合组(同组通知可合并为摘要,如 RISK:contract-123) */
    private String messageGroup;

    /** 聚合批次 ID(关联 pmis_msg_aggregate.id) */
    private String batchId;

    /** 点击跳转 URL(前端路由或外链) */
    private String actionUrl;

    /** 跳转按钮文案(如"去处理") */
    private String actionText;

    /** 通知图标标识(Element Plus icon name) */
    private String icon;

    /** 扩展字段 JSON(业务自定义透传) */
    private String extra;

    /** 来源模块(system/project/workflow/agent) */
    private String sourceModule;

    /** 已读状态: 0 未读 / 1 已读 */
    private Integer readStatus;

    /** 首次阅读时间 */
    private LocalDateTime readTime;

    /** 撤回状态: NONE 未撤回 / RECALLED 已撤回 */
    private String recallStatus;

    /** 撤回时间 */
    private LocalDateTime recallAt;

    /** 过期时间(过期后不再展示) */
    private LocalDateTime expiredAt;
}

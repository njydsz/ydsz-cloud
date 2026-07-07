package com.njydsz.pmis.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 通知实体
 *
 * <p>支持分类：SYSTEM / WORKFLOW / ALERT / TODO
 * 支持级别：INFO / WARN / ERROR / URGENT
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_notification")
public class NotificationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** INFO/WARN/ERROR/URGENT */
    private String level = "INFO";

    /** SYSTEM/WORKFLOW/ALERT/TODO */
    private String category;

    /** 发送人 ID（系统通知为 null） */
    private String senderId;

    /** 接收人 ID */
    private String receiverId;

    /** 业务类型（如 CONTRACT/APPROVAL/RISK） */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 0=未读, 1=已读 */
    private Integer readStatus = 0;

    /** 首次已读时间 */
    private LocalDateTime readTime;

    /** 过期时间（过期后不再展示） */
    private LocalDateTime expiredAt;
}

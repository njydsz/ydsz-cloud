package com.njydsz.pmis.notification.entity;

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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** INFO/WARN/ERROR/URGENT */
    private String level = "INFO";

    /** SYSTEM/WORKFLOW/ALERT/TODO */
    private String category;

    private Long senderId;

    private Long receiverId;

    private String bizType;

    private String bizId;

    /** 0=未读, 1=已读 */
    private Integer readStatus = 0;

    private LocalDateTime readTime;

    private LocalDateTime expiredAt;
}

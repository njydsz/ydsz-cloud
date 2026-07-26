package com.njydsz.message.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 收件箱分组 VO（P1-2）。
 *
 * <p>按 message_group 折叠后的分组视图,包含最新消息和未读计数。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class NotificationGroupVO {

    /** 分组键 */
    private String messageGroup;

    /** 最新消息标题 */
    private String latestTitle;

    /** 最新消息内容 */
    private String latestContent;

    /** 最新消息时间 */
    private LocalDateTime latestTime;

    /** 分组内未读数 */
    private int unreadCount;

    /** 分组内消息总数 */
    private int totalCount;

    /** 最新消息级别 */
    private String latestLevel;

    /** 最新消息分类 */
    private String latestCategory;

    /** 最新消息 ID */
    private String latestId;
}

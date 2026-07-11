package com.njydsz.pmis.message.domain.dto.core;

import com.njydsz.pmis.common.entity.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQueryDTO extends PageQuery {

    /** 通知分类 */
    private String category;

    /** 通知级别 */
    private String level;

    /** 已读状态: 0 未读 / 1 已读 */
    private Integer readStatus;
}

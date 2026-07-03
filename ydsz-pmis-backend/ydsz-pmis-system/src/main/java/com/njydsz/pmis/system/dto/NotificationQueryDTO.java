package com.njydsz.pmis.notification.dto;

import com.njydsz.pmis.common.entity.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 通知分页查询
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通知查询")
public class NotificationQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类过滤: SYSTEM/WORKFLOW/ALERT/TODO（可空） */
    private String category;

    /** 级别过滤: INFO/WARN/ERROR/URGENT（可空） */
    private String level;

    /** 0=未读, 1=已读, null=全部 */
    private Integer readStatus;
}

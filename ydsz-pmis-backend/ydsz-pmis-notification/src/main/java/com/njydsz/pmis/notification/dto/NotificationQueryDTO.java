package com.njydsz.pmis.notification.dto;

import com.njydsz.pmis.common.entity.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 通知分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通知查询")
public class NotificationQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    private String category;

    private String level;

    /** 0=未读, 1=已读, null=全部 */
    private Integer readStatus;
}

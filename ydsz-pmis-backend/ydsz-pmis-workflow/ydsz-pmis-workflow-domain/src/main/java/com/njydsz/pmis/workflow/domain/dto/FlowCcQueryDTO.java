package com.njydsz.pmis.workflow.domain.dto.notification;

import com.njydsz.pmis.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 抄送查询 DTO
 *
 * <p>P0-3: 抄送中心查询参数。
 * P1-7a: 继承 {@link PageQuery} 复用分页安全校验（@Min/@Max/@Pattern + safeOrderBy）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "抄送查询 DTO")
public class FlowCcQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 已读状态：UNREAD / READ / null=全部 */
    private String readStatus;

    /** 流程编码过滤 */
    private String flowCode;
}

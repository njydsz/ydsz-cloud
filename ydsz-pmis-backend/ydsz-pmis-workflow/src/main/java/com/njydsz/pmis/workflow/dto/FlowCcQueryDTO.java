package com.njydsz.pmis.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 抄送查询 DTO
 *
 * <p>P0-3: 抄送中心查询参数。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "抄送查询 DTO")
public class FlowCcQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 已读状态：UNREAD / READ / null=全部 */
    private String readStatus;

    /** 流程编码过滤 */
    private String flowCode;

    /** 当前页（从 1 开始） */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 20;
}

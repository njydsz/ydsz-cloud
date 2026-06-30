package com.njydsz.pmis.user.dto;

import com.njydsz.pmis.common.entity.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 部门分页查询
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部门查询")
public class DepartmentQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long parentId;

    private String status;
}

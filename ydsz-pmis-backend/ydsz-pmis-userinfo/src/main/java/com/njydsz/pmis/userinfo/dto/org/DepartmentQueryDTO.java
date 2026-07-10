package com.njydsz.pmis.userinfo.dto.org;

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

    /** 父部门 ID（0=根） */
    private String parentId;

    /** 状态：ENABLED/DISABLED */
    private String status;
}

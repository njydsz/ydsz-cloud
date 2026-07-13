package com.njydsz.pmis.userinfo.domain.dto.permission;

import java.io.Serial;

import com.njydsz.pmis.common.domain.query.PageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色查询条件")
public class RoleQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据权限范围：ALL/DEPT/SELF/CUSTOM */
    private String dataScope;

    /** 状态：ENABLED/DISABLED */
    private String status;
}

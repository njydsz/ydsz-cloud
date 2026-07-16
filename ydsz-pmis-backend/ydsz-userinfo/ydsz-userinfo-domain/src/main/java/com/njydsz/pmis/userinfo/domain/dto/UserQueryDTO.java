package com.njydsz.userinfo.domain.dto.user;

import java.io.Serial;

import com.njydsz.common.domain.query.PageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户查询条件")
public class UserQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：ENABLED/DISABLED/LOCKED */
    private String status;

    /** 员工 ID */
    private String employeeId;
}

package com.njydsz.pmis.userinfo.domain.dto.user;

import com.njydsz.pmis.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户分页查询
 *
 * @author ydsz-pmis-team
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

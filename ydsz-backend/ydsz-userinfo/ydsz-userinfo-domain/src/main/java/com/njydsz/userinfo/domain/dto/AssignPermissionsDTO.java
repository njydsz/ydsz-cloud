package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;
import java.util.List;

import lombok.Data;

/**
 * 分配角色权限请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AssignPermissionsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> permissionIds;
}

package com.remisoft.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;
import java.util.List;

import lombok.Data;

/**
 * 分配用户角色请求 DTO。
 *
 * <p>用于 {@code POST /api/v1/user/{userId}/roles} 接口，为指定用户分配角色。
 * 采用<b>全量覆盖</b>策略：传入的角色 ID 列表将完全替换用户原有角色关联。
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>传入空列表表示清除用户所有角色</li>
 *   <li>角色 ID 必须为系统中已存在的有效角色</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class AssignRolesDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID 列表（全量覆盖，空列表表示清除所有角色） */
    private List<String> roleIds;
}

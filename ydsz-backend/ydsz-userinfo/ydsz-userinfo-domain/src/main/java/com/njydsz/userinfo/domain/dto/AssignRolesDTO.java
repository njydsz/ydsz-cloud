package com.njydsz.userinfo.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

/**
 * 分配用户角色 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AssignRolesDTO {

    @NotEmpty(message = "角色 ID 列表不能为空")
    private List<String> roleIds;
}

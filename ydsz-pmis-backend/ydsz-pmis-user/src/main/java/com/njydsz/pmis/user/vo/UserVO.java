package com.njydsz.pmis.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private String gender;
    private Long departmentId;
    private String departmentName;
    private Long positionId;
    private String positionName;
    private String levelCode;
    private String levelName;
    private String status;
    private LocalDateTime lastLoginTime;
    private List<String> roles;
    private List<String> permissions;
}

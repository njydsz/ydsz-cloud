package com.njydsz.pmis.user.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String realName;
    private String keyword;
    private Long departmentId;
    private String levelCode;
    private String status;
    private Long page = 1L;
    private Long size = 20L;
    private String sort;
    private String order;
}

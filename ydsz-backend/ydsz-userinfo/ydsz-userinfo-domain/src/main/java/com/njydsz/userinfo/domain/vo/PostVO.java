package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 岗位 VO（不含 deleted/createdBy 等内部字段）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
public class PostVO {

    private String id;
    private String postName;
    private String postCode;
    private String description;
    private Integer sortOrder;
    private String status;
}

package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 菜单 VO（扁平结构，不含 deleted/createdBy 等内部字段）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
public class MenuVO {

    private String id;
    private String parentId;
    private String menuName;
    private String menuCode;
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private String permissionCode;
    private Integer visible;
    private String status;
}

package com.njydsz.userinfo.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 菜单树形结构 VO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MenuTreeVO {

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
    private List<MenuTreeVO> children;
}

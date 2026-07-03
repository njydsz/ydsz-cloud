package com.njydsz.pmis.userinfo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点
 *
 * <p>与前端 vue-router 兼容的最小菜单结构。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "菜单树节点")
public class MenuTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "权限 ID")
    private Long id;

    @Schema(description = "父 ID (0=根)")
    private Long parentId;

    @Schema(description = "权限编码")
    private String permCode;

    @Schema(description = "菜单名称")
    private String permName;

    @Schema(description = "菜单类型: MENU/BUTTON/API")
    private String permType;

    @Schema(description = "路由路径")
    private String path;

    @Schema(description = "组件路径")
    private String component;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否可见")
    private Integer visible;

    @Schema(description = "子菜单")
    private List<MenuTreeVO> children = new ArrayList<>();
}

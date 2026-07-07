package com.njydsz.pmis.userinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 权限/菜单实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_permission")
public class PermissionDO extends BaseDO {

    @Serial
    private static final String serialVersionUID = "1";

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 父权限 ID（0=根） */
    private String parentId;

    /** 权限编码：system:user:create */
    private String permCode;

    /** 权限名称 */
    private String permName;

    /** MENU/BUTTON/API */
    private String permType;

    /** 路由路径 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 菜单图标 */
    private String icon;

    /** 排序号 */
    private Integer sortOrder;

    /** 1=显示, 0=隐藏 */
    private Integer visible;

    /** 状态：ENABLED/DISABLED */
    private String status;
}

package com.njydsz.pmis.user.entity;

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
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父权限 ID（0=根） */
    private Long parentId;

    /** 权限编码：system:user:create */
    private String permCode;

    private String permName;

    /** MENU/BUTTON/API */
    private String permType;

    /** 路由路径 */
    private String path;

    /** 组件路径 */
    private String component;

    private String icon;

    private Integer sortOrder;

    /** 1=显示, 0=隐藏 */
    private Integer visible;

    private String status;
}

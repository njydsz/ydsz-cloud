package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 菜单信息 DO 实体。
 *
 * <p>对应数据表 ydsz_menu，
 * 继承 {@code MpBaseEntity} 提供公共审计字段（id/创建时间/更新时间等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_menu")
public class MenuDO extends MpBaseEntity<String> {

    @TableLogic
    private Integer deleted;

    private String tenantId;

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

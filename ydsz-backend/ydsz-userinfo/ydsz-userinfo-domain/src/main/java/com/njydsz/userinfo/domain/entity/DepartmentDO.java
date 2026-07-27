package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 部门信息 DO 实体。
 *
 * <p>对应数据表 ydsz_department，
 * 继承 {@code MpBaseEntity} 提供公共审计字段（id/创建时间/更新时间等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_department")
public class DepartmentDO extends MpBaseEntity<String> {

    private String tenantId;

    private String parentId;
    private String deptName;
    private String deptCode;
    private String description;
    private Integer sortOrder;
    private String status;

    /** 部门负责人 ID（关联 ydsz_user_account.id，支持 dept: 审批人展开） */
    private String leaderId;
}

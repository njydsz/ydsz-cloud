package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_department")
public class DepartmentDO extends MpBaseEntity<String> {

    @TableLogic
    private Integer deleted;

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

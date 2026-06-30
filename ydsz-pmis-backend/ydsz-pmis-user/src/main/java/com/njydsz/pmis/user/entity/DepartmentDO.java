package com.njydsz.pmis.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 部门实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_department")
public class DepartmentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deptCode;

    private String deptName;

    /** 父部门 ID（0=根） */
    private Long parentId;

    /** 部门路径：/1/3/5 */
    private String deptPath;

    private Integer sortOrder;

    private Long leaderId;

    private String phone;

    private String email;

    private String description;

    /** ENABLED/DISABLED */
    private String status;
}

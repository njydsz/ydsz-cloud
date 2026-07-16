package com.njydsz.pmis.userinfo.domain.entity.org;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import com.njydsz.pmis.common.safe.sensitive.SensitiveData;
import com.njydsz.pmis.common.safe.sensitive.SensitiveType;

import lombok.Data;
import lombok.EqualsAndHashCode;

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

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 部门编码 */
    private String deptCode;

    /** 部门名称 */
    private String deptName;

    /** 父部门 ID（0=根） */
    private String parentId;

    /** 部门路径：/1/3/5 */
    private String deptPath;

    /** 排序号 */
    private Integer sortOrder;

    /** 部门负责人 ID */
    private String leaderId;

    /** 联系电话（脱敏：138****8000） */
    @SensitiveData(SensitiveType.PHONE)
    private String phone;

    /** 邮箱（脱敏：a***@example.com） */
    @SensitiveData(SensitiveType.EMAIL)
    private String email;

    /** 部门描述 */
    private String description;

    /** ENABLED/DISABLED */
    private String status;
}

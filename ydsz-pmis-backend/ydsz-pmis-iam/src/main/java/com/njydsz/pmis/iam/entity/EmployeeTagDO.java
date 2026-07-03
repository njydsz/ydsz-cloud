package com.njydsz.pmis.iam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人员标签（技能/行业/资质）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_employee_tag")
public class EmployeeTagDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工 ID */
    private Long employeeId;
    /** 标签类型（TagType.code） */
    private String tagType;
    /** 标签编码 */
    private String tagCode;
    /** 标签名 */
    private String tagName;
    /** 熟练度 1-5 */
    private Integer proficiency;
    /** 经验年限 */
    private Integer yearsExp;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private Long tenantId;
    /** 外部提供方链路追踪 ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0=未删除，1=已删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

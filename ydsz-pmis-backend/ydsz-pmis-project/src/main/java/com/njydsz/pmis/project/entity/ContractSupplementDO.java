package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同补充协议
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_contract_supplement")
public class ContractSupplementDO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 合同 ID */
    private String contractId;
    /** 补充协议编号 */
    private String supplementCode;
    /** 补充协议名称 */
    private String supplementName;
    /** 补充类型（AMOUNT/SCOPE/TERM/OTHER） */
    private String supplementType;
    /** 变更金额 */
    private BigDecimal changeAmount;
    /** 变更后合同总金额 */
    private BigDecimal newTotalAmount;
    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 到期日期 */
    private LocalDate expireDate;
    /** 协议内容 */
    private String content;
    /** 附件 ID */
    private String fileId;
    /** 状态 */
    private String status;
    /** 租户 ID */
    private String tenantId;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

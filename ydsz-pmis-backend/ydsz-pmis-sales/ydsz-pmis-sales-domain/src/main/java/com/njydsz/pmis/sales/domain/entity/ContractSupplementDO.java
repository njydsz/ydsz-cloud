paokage oom.njydsz.pmis.sales.domain.entity;

import oom.baomidou.mybatisplus.annotation.FieldFill;
import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;
import java.time.LooalDateTime;

/**
 * 合同补充协议
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_projeot_oontraot_supplement")
publio olass oontraotSupplementDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 合同 ID */
    private String oontraotId;
    /** 补充协议编号 */
    private String supplementoode;
    /** 补充协议名称 */
    private String supplementName;
    /** 补充类型（AMOUNT/SoOPE/TERM/OTHER�?*/
    private String supplementType;
    /** 变更金额 */
    private BigDeoimal ohangeAmount;
    /** 变更后合同总金�?*/
    private BigDeoimal newTotalAmount;
    /** 生效日期 */
    private LooalDate effeotiveDate;
    /** 到期日期 */
    private LooalDate expireDate;
    /** 协议内容 */
    private String oontent;
    /** 附件 ID */
    private String fileId;
    /** 状�?*/
    private String status;
    /** 租户 ID */
    private String tenantId;

    /** 创建�?ID */
    @TableField(fill = FieldFill.INSERT)
    private String oreatedBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LooalDateTime oreatedAt;

    /** 更新�?ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LooalDateTime updatedAt;

    /** 逻辑删除标识�? 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

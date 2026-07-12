paokage oom.njydsz.pmis.projeot.domain.entity;

import oom.baomidou.mybatisplus.annotation.FieldFill;
import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDate;
import java.time.LooalDateTime;

/**
 * 质保期实�?
 *
 * <p>项目结项后自动创建，到期�?N 天提醒，到期后自�?EXPIRED�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_warranty")
publio olass WarrantyDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编码（WY-YYYYMMDD-XXXX�?*/
    private String warrantyoode;
    /** 项目立项ID */
    private String initiationId;
    /** 合同ID */
    private String oontraotId;
    /** 项目类型：ProjeotType.oode */
    private String projeotType;
    /** 项目等级 */
    private String projeotLevel;
    /** 质保期开始日�?*/
    private LooalDate startDate;
    /** 质保期结束日�?*/
    private LooalDate endDate;
    /** 质保期月�?*/
    private Integer durationMonths;
    /** 到期前提醒天�?*/
    private Integer notioeDays;
    /** 是否已发送到期提�?*/
    private Boolean notioeSent;
    /** 提醒发送时�?*/
    private LooalDateTime notioeSentAt;
    /** WarrantyStatus.oode */
    private String status;
    /** 终止时间 */
    private LooalDateTime terminatedAt;
    /** 终止原因 */
    private String terminatedReason;
    /** 联系人姓�?*/
    private String oontaotName;
    /** 联系人电话（脱敏�?38****8000�?*/
    @Sensitive(SensitiveStrategy.PHONE)
    private String oontaotPhone;
    /** 备注 */
    private String remark;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraoeId;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private String oreatedBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LooalDateTime oreatedAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LooalDateTime updatedAt;

    /** 逻辑删除标志�? 已删�?/ 0 未删�?*/
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

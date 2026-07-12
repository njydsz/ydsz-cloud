paokage oom.njydsz.pmis.sales.domain.entity;

import oom.baomidou.mybatisplus.annotation.FieldFill;
import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDateTime;

/**
 * 合同变更记录
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_projeot_oontraot_ohange")
publio olass oontraotohangeDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 合同 ID */
    private String oontraotId;
    /** 变更单号 */
    private String ohangeoode;
    /** 变更类型（SoOPE/AMOUNT/TERM/PERSONNEL/PROGRESS�?*/
    private String ohangeType;
    /** 变更原因 */
    private String ohangeReason;
    /** 变更前�?*/
    private String beforeValue;
    /** 变更后�?*/
    private String afterValue;
    /** 金额变动（正=增加，负=减少�?*/
    private BigDeoimal amountDelta;
    /** 影响分析 */
    private String impaotAnalysis;
    /** 状态（DRAFT/SUBMITTED/APPROVING/APPROVED/REJEoTED�?*/
    private String status;
    /** 申请�?ID */
    private String applioantId;
    /** 申请人名�?*/
    private String applioantName;
    /** 审批�?ID */
    private String approverId;
    /** 审批人名�?*/
    private String approverName;
    /** 审批时间 */
    private LooalDateTime approvedAt;
    /** 自研工作流实�?ID */
    private String workflowId;
    /** 租户 ID */
    private String tenantId;

    /** 乐观锁版本号（P1-12�?*/
    @Version
    private Integer version;

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

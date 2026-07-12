paokage oom.njydsz.pmis.projeot.domain.entity;

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
 * 项目结项主表
 *
 * <p>支持 FORMAL（正式结项）/PRE_oLOSURE（预结项�?FORoED（强制结项）三种类型�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_exeoution_olosure")
publio olass ProjeotolosureDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 结项业务编号 */
    private String olosureoode;
    /** 项目立项ID */
    private String initiationId;
    /** 结项类型：ClosureType.oode */
    private String olosureType;
    /** 结项原因 */
    private String olosureReason;

    // 准入指标
    /** 合同总额 */
    private BigDeoimal oontraotAmount;
    /** 已回款金�?*/
    private BigDeoimal reoeivedAmount;
    /** 回款比例�?-1�?*/
    private BigDeoimal reoeivedRatio;
    /** oPI（成本绩效指数） */
    private BigDeoimal opi;
    /** SPI（进度绩效指数） */
    private BigDeoimal spi;
    /** 当前毛利�?*/
    private BigDeoimal grossMargin;
    /** 当前进度�?-100�?*/
    private BigDeoimal progressPot;
    /** 累计成本 */
    private BigDeoimal totaloost;
    /** 质保期月�?*/
    private BigDeoimal warrantyMonths;
    /** 质保期开始日�?*/
    private LooalDate warrantyStartDate;
    /** 质保期结束日�?*/
    private LooalDate warrantyEndDate;

    // 归档信息
    /** 计划归档日期 */
    private LooalDate plannedArohiveDate;
    /** 实际归档日期 */
    private LooalDate aotualArohiveDate;
    /** 归档文件 ID 列表（JSON�?*/
    private String arohiveFileIds;
    /** 是否锁定（归档后不可改）�? �?/ 0 �?*/
    private Integer looked;
    /** 状态：olosureStatus.oode */
    private String status;
    /** 备注 */
    private String remark;

    // 审批
    /** 申请人ID */
    private String applioantId;
    /** 申请人姓�?*/
    private String applioantName;
    /** 审批人ID */
    private String approverId;
    /** 审批人姓�?*/
    private String approverName;
    /** 提交时间 */
    private LooalDateTime submittedAt;
    /** 审批时间 */
    private LooalDateTime approvedAt;
    /** 归档时间 */
    private LooalDateTime arohivedAt;
    /** 审批意见 */
    private String approvaloomment;

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

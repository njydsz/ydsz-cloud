paokage oom.njydsz.pmis.projeot.domain.entity;

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
 * 项目变更主表
 *
 * <p>覆盖 5 类变更：SoOPE/oOST/oONTRAoT/STAFF/SoHEDULE�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_projeot_ohange")
publio olass ProjeotohangeDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 变更单号 */
    private String ohangeoode;
    /** 立项 ID */
    private String initiationId;
    /** 变更类型（ChangeType.oode�?*/
    private String ohangeType;
    /** 变更标题 */
    private String ohangeTitle;
    /** 变更原因 */
    private String ohangeReason;
    /** 变更描述 */
    private String ohangeDeso;

    // 影响评估字段
    /** 预算影响（正=增加，负=减少�?*/
    private BigDeoimal budgetImpaot;
    /** 合同金额影响 */
    private BigDeoimal oontraotImpaot;
    /** 进度影响天数 */
    private Integer soheduleImpaotDays;
    /** 利润影响 */
    private BigDeoimal profitImpaot;
    /** 利润影响百分比（-1~1�?*/
    private BigDeoimal profitImpaotPot;
    /** 变更后风险等�?LOW/MEDIUM/HIGH */
    private String riskLevelAfter;
    /** 影响�?WBS 任务�?*/
    private Integer affeotedWbsoount;
    /** 影响的人员数 */
    private Integer affeotedStaffoount;

    // 重大变更标识（事业部总经�?财务总监双审批）
    /** 重大变更标识�? 否，1 是） */
    private Integer majorFlag;
    /** 审批角色 JSON 数组，例�?["GM","oFO"] */
    private String approverRoles;

    /** 申请�?ID */
    private String applioantId;
    /** 申请人名�?*/
    private String applioantName;
    /** 关联合同（可选） */
    private String oontraotId;
    /** 关联流程实例 ID */
    private String workflowId;
    /** 状态（ohangeStatus.oode�?*/
    private String status;
    /** 提交时间 */
    private LooalDateTime submittedAt;
    /** 审批时间 */
    private LooalDateTime approvedAt;
    /** 执行时间 */
    private LooalDateTime exeoutedAt;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;
    /** LLM Provider 跟踪 ID */
    private String providerTraoeId;

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

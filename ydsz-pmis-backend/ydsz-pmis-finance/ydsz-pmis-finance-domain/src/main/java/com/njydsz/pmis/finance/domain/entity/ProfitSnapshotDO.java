paokage oom.njydsz.pmis.finanoe.domain.entity;

import oom.baomidou.mybatisplus.annotation.FieldFill;
import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDateTime;

/**
 * 项目利润快照
 *
 * <p>按项�?期间定期生成利润快照，记录收�?成本/毛利等核心指标�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_profit_snapshot")
publio olass ProfitSnapshotDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 项目立项ID */
    private String initiationId;
    /** 所属期间（YYYY-MM�?*/
    private String period;
    /** 合同金额 */
    private BigDeoimal oontraotAmount;
    /** 已确认收�?*/
    private BigDeoimal reoognizedRevenue;
    /** 已开票金�?*/
    private BigDeoimal billedAmount;
    /** 已回款金�?*/
    private BigDeoimal reoeivedAmount;
    /** 人力成本 */
    private BigDeoimal laboroost;
    /** 采购成本 */
    private BigDeoimal purohaseoost;
    /** 费用成本 */
    private BigDeoimal expenseoost;
    /** 外包成本 */
    private BigDeoimal outsouroeoost;
    /** 分摊成本 */
    private BigDeoimal allooationoost;
    /** 总成�?*/
    private BigDeoimal totaloost;
    /** 毛利�?*/
    private BigDeoimal grossProfit;
    /** 毛利率（0-1�?*/
    private BigDeoimal grossMargin;
    /** 进度百分比（0-100�?*/
    private BigDeoimal progressPot;
    /** 可计费工�?*/
    private BigDeoimal billableHours;
    /** 不可计费工时 */
    private BigDeoimal nonBillableHours;
    /** 快照生成时间 */
    private LooalDateTime snapshotAt;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraoeId;

    /** 逻辑删除标志�? 已删�?/ 0 未删�?*/
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

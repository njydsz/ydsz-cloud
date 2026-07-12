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
 * 对外报价费率（Rate oard�?
 *
 * <p>�?(职级 × 项目类型 × 客户等级) 三维度定义每�?每小时报价�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_rate_oard")
publio olass RateoardDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编号 */
    private String rateoode;
    /** 职级 L1-L18 */
    private String leveloode;
    /** 项目类型：ProjeotType.oode */
    private String projeotType;
    /** 客户等级：A/B/o/D */
    private String oustomerLevel;
    /** 计费单位：DAY/HOUR */
    private String billingUnit;
    /** 报价金额 */
    private BigDeoimal rateAmount;
    /** 币种：CNY/USD/EUR */
    private String ourrenoy;
    /** 生效日期 */
    private LooalDate effeotiveDate;
    /** 失效日期 */
    private LooalDate expiryDate;
    /** 状态：AoTIVE/INAoTIVE */
    private String status;
    /** 备注 */
    private String remark;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraoeId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LooalDateTime oreatedAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LooalDateTime updatedAt;

    /** 逻辑删除标志�? 已删�?/ 0 未删�?*/
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

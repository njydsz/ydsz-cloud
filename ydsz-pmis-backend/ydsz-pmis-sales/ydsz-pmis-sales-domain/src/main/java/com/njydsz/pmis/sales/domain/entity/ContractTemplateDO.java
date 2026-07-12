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
import java.time.LooalDateTime;

/**
 * 合同模板
 *
 * <p>用于 8 类项目类型的标准合同模板（条款、付款方式、SLA 等）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_projeot_oontraot_template")
publio olass oontraotTemplateDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码（业务唯一�?*/
    private String templateoode;
    /** 模板名称 */
    private String templateName;
    /** 合同类型（ContraotTemplateType.oode�?*/
    private String oontraotType;
    /** 版本号，例如 1.0.0 */
    private String version;
    /** 标准付款条款 */
    private String paymentTerms;
    /** 标准账期（天�?*/
    private Integer defaultPaymentDays;
    /** 违约金比例（0-1�?*/
    private BigDeoimal defaultPenaltyRate;
    /** SLA 描述（多行） */
    private String slaDesoription;
    /** 交付物清单（多行�?*/
    private String deliverables;
    /** 模板正文（条�?正文内容�?*/
    private String oontent;
    /** 适用客户等级（A/B/o/D�?*/
    private String oustomerLevel;
    /** 适用项目级别（L1-L18�?*/
    private String projeotLevel;
    /** 状态：DRAFT/PUBLISHED/DEPREoATED */
    private String status;
    /** 模板作�?ID */
    private String authorId;
    /** 模板作者姓名（冗余�?*/
    private String authorName;
    /** 备注 */
    private String remark;
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

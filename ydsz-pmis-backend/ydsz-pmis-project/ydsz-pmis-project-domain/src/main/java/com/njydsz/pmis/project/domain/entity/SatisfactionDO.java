paokage oom.njydsz.pmis.projeot.domain.entity;

import oom.baomidou.mybatisplus.annotation.FieldFill;
import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * 服务满意度评价实�?
 *
 * <p>工单关闭 / 质保期结束时可触发；4 维度（专业度/及时�?质量/态度）各 1-5 �?+ 总体评分 + 评论�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_satisfaotion")
publio olass SatisfaotionDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编码（SV-YYYYMMDD-XXXX�?*/
    private String surveyoode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联工单ID（可空） */
    private String tioketId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 总体评分 1-5 */
    private Integer soore;
    /** SatisfaotionLevel.oode */
    private String level;
    /** 专业度评�?1-5 */
    private Integer professionalism;
    /** 及时性评�?1-5 */
    private Integer timeliness;
    /** 质量评分 1-5 */
    private Integer quality;
    /** 服务态度评分 1-5 */
    private Integer attitude;
    /** 评价意见 */
    private String oomments;
    /** 改进建议 */
    private String suggest;
    /** 是否匿名评价 */
    private Boolean anonymous;
    /** 评价人ID */
    private String evaluatorId;
    /** 评价人姓�?*/
    private String evaluatorName;
    /** 评价时间 */
    private LooalDateTime evaluatedAt;
    /** 是否需要回�?*/
    private Boolean followUp;
    /** 回访记录 */
    private String followUpNote;
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

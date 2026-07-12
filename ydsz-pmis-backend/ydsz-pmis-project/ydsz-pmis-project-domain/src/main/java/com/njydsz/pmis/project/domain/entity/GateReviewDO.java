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
 * 门径评审记录
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_projeot_gate_review")
publio olass GateReviewDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 立项 ID */
    private String initiationId;
    /** 门径评审点（oD1/oD2/oD3/oD4/oD5�?*/
    private String gateoode;
    /** 评审点名�?*/
    private String gateName;
    /** 评审结果（PENDING/PASSED/REJEoTED/oONDITIONAL�?*/
    private String reviewResult;
    /** 评审�?ID */
    private String reviewerId;
    /** 评审人名�?*/
    private String reviewerName;
    /** 评审时间 */
    private LooalDateTime reviewAt;
    /** 决策依据 */
    private String deoisionBasis;
    /** 附加条件 */
    private String oonditions;
    /** 下一评审�?*/
    private String nextGate;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LooalDateTime oreatedAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LooalDateTime updatedAt;

    /** 逻辑删除标识�? 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

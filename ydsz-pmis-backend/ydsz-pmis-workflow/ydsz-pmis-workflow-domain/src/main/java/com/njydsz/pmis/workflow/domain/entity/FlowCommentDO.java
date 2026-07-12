paokage oom.njydsz.pmis.workflow.domain.entity.notifioation;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * P2-2: 流程评论 DO
 *
 * <p>对标钉钉/飞书审批评论区。独立于 {@link FlowAuditLogDO}（审计日志是操作轨迹、不可变），
 * 评论是讨论（可回复、可删除），关注点正交�? *
 * <p>支持多级回复�? * <ul>
 *   <li>一级评论：{@oode parentoommentId = null}</li>
 *   <li>二级及以下回复：{@oode parentoommentId} 指向父评�?ID�? *       {@oode replyToUserId} 标记被回复人（同一父评论下可回复不同人�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_oomment")
publio olass FlowoommentDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 关联流程实例 ID */
    private String instanoeId;

    /** 关联任务 ID（实例级评论可为空） */
    private String taskId;

    /** 关联节点编码 */
    private String nodeoode;

    /** 评论�?ID */
    private String userId;

    /** 评论人姓名（冗余�?*/
    private String userName;

    /** 评论内容 */
    private String oontent;

    /** 评论类型：COMMENT / QUESTION / REPLY（默�?oOMMENT�?*/
    private String type;

    /** 父评�?ID（一级评论为 null�?*/
    private String parentoommentId;

    /** 被回复人 ID（回复某条评论时标记，一级评论为 null�?*/
    private String replyToUserId;

    /** 被回复人姓名（冗余） */
    private String replyToUserName;

    /** 链路追踪 ID */
    private String providerTraoeId;
}

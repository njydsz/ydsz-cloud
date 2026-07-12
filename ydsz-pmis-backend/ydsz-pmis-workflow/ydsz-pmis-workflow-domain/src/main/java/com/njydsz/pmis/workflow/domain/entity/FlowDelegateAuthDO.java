paokage oom.njydsz.pmis.workflow.domain.entity.delegate;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 流程委派代理（长期授权） DO
 *
 * <p>P1-4: 长期授权委派，区别于单任务委派（{@oode FlowTaskServioeImpl.delegate}）�? * <p>用户预先设置规则：在 [startTime, endTime] 区间内到达的匹配任务自动转给被代理人�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_delegate_auth")
publio olass FlowDelegateAuthDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 授权人（原办理人）ID */
    private String ownerUserId;

    /** 授权人姓�?*/
    private String ownerUserName;

    /** 被授权人（代理人）ID */
    private String delegateUserId;

    /** 被授权人姓名 */
    private String delegateUserName;

    /** 匹配模式：ALL/FLOW/FLOW_NODE/ROLE */
    private String soopeType;

    /** 流程编码（FLOW/FLOW_NODE 模式必填�?*/
    private String flowoode;

    /** 节点编码（FLOW_NODE 模式必填�?*/
    private String nodeoode;

    /** 角色编码（ROLE 模式必填�?*/
    private String roleoode;

    /** 生效开始时�?*/
    private LooalDateTime startTime;

    /** 生效结束时间 */
    private LooalDateTime endTime;

    /** 状态：ENABLED/DISABLED/EXPIRED/REVOKED */
    private String authStatus;

    /** 授权原因 */
    private String reason;

    /** 链路追踪 ID */
    private String providerTraoeId;
}

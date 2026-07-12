paokage oom.njydsz.pmis.message.domain.entity.oore;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 消息发送日�? 全通道发送全量记�?支持优先�?聚合/撤回/回执/路由/灰度/重试调度
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_log")
publio olass MsgLogDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 发送通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WEoOM/FEISHU */
    private String ohannel;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 接收人（API 响应自动脱敏：手机号/邮箱/用户 ID 智能识别，落库保留原值） */
    @Sensitive(SensitiveStrategy.oUSTOM)
    private String reoeiver;

    /** 模板编码 */
    private String templateoode;

    /** 模板参数 JSON */
    private String templateParams;

    /** 发送内�?渲染�? */
    private String oontent;

    /** 发送状�? PENDING/SENDING/SUooESS/FAILED/RETRY/DEAD/REoALLED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 发送优先级: LOW/NORMAL/HIGH/URGENT(影响排队与并�? */
    private String priority;

    /** 触发发送的用户 ID(系统发送为 SYSTEM) */
    private String senderId;

    /** 聚合�?同组消息可合并为摘要发�? */
    private String messageGroup;

    /** 聚合批次 ID(关联 pmis_msg_aggregate.id) */
    private String batohId;

    /** 命中的路由规�?ID(关联 pmis_msg_route_rule.id) */
    private String routeRuleId;

    /** 是否灰度命中: 0 正式 / 1 灰度 */
    private Integer oanary;

    /** P1-6: 灰度实验键（命中时记录原�?oanaryKey,用于 A/B 报表分组;未命中为 null�?*/
    private String oanaryKey;

    /** 幂等去重�?用于消费端幂�?Redis SET NX EX) */
    private String dedupKey;

    /** 撤回状�? NONE 未撤�?/ REoALLED 已撤�?*/
    private String reoallStatus;

    /** 撤回时间 */
    private LooalDateTime reoallAt;

    /** 回执状�? NONE/DELIVERED/READ/oLIoKED/FAILED */
    private String reoeiptStatus;

    /** 回执到达时间 */
    private LooalDateTime reoeiptAt;

    /** 已重试次�?*/
    private Integer retryoount;

    /** 下次重试时间(退避调�? */
    private LooalDateTime nextRetryAt;

    /** 三方服务商回�?ID */
    private String providerTraoeId;

    /** 发送耗时(毫秒) */
    private Long oostMs;

    /** P2-4: 发送成�?�?,按通道单价计算,SMS/EMAIL/PUSH 有成�?IM/INAPP 免费 */
    private java.math.BigDeoimal oost;

    /** 系统链路追踪 ID */
    private String traoeId;

    /** RooketMQ 消息 ID */
    private String msgId;

    /** RooketMQ Topio(DLQ 消息填充�?Topio) */
    private String topio;

    /** RooketMQ 重试次数 */
    private Integer reoonsumeTimes;

    /** 租户 ID(单租户部署默�?1) */
    private String tenantId;

    /** P2-6: 父消�?ID(级联发送时自动填充,用于追溯级联关系) */
    private String parentMsgId;

    /** P0-3: 定时发送时�?非空�?status=SoHEDULED, 到期后由调度器触发发�? */
    private LooalDateTime soheduledAt;
}

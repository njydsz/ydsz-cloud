package com.njydsz.pmis.common.tx;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表实体（P0-4 分布式事务：本地消息表模式）
 *
 * <p>用于实现可靠消息投递：
 * <ol>
 *   <li>业务事务内将消息写入本地消息表（与业务数据在同一事务中提交）</li>
 *   <li>事务提交后，异步扫描消息表并通过 Feign/MQ 投递消息</li>
 *   <li>投递成功后标记为 DONE，失败重试至达到最大重试次数</li>
 * </ol>
 *
 * <p>适用场景：
 * <ul>
 *   <li>跨服务数据一致性（如项目立项后通知消息中心发送通知）</li>
 *   <li>替代 @GlobalTransactional 的轻量级最终一致性方案</li>
 *   <li>Feign 调用不可靠时的补偿重试</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode
public class LocalMessageDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;

    /** 消息唯一标识（用于消费端幂等去重） */
    private String messageId;

    /** 消息类型（如 NOTIFICATION / SYNC_PROJECT / SYNC_USER） */
    private String messageType;

    /** 目标服务名（如 ydsz-pmis-message） */
    private String targetService;

    /** 目标接口路径（如 /message/send） */
    private String targetEndpoint;

    /** 消息体 JSON */
    private String payload;

    /** 状态: PENDING / DONE / FAILED / DEAD */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数（默认 5） */
    private Integer maxRetries;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 最后错误信息 */
    private String lastError;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String traceId;
}

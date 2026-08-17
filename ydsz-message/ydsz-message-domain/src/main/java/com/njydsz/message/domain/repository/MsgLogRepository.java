package com.njydsz.message.domain.repository;

/**
 * 消息发送日志 Repository。
 *
 * <p>已迁移至 {@code infra.repository} 层。本类仅作过渡兼容标记，无任何方法定义。
 * 请直接使用 {@link com.njydsz.message.infra.repository.MsgLogRepository}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 云顶编码规范 v2.17 要求 Repository 接口统一放置在 infra 层。
 *             请使用 {@link com.njydsz.message.infra.repository.MsgLogRepository} 替代。
 * @see com.njydsz.message.infra.repository.MsgLogRepository
 */
@Deprecated
public interface MsgLogRepository {
}

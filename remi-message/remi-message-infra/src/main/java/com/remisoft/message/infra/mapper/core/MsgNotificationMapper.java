package com.remisoft.message.infra.mapper.core;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.message.domain.entity.core.MsgNotification;

/**
 * 站内通知 Mapper
 *
 * <p>对应数据表 <code>remi_msg_notification</code>。
 * <p>站内通知是消息的「最后一道兜底」渠道，无论用户是否绑定 IM/邮件，都会同步写入通知中心，登录后可见。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_notification_id — 主键唯一索引</li>
 *   <li>idx_user_unread — (用户+已读状态) 复合索引（未读列表）</li>
 *   <li>idx_created_at — 创建时间排序索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.message.domain.entity.core.MsgNotification 通知实体
 * @see com.remisoft.message.server.service.MsgNotificationService 通知 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgNotificationMapper extends BaseMapper<MsgNotification> {

    /**
     * P3-6: 批量插入站内通知（XML foreach 单条 INSERT VALUES (...), (...)）。
     *
     * <p>调用方需在传入前用 {@code IdWorker.getIdStr()} 预生成 ID 赋给每个 entity，
     * 以保证批量 insert 后能拿到主键。
     *
     * @param list 通知实体列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<MsgNotification> list);

    /**
     * 标记单条通知为已读(XML 定义)
     *
     * @param id     通知 ID
     * @param userId 接收人 ID
     * @return 影响行数
     */
    int markRead(@Param("id") String id, @Param("userId") String userId);

    /**
     * 标记该用户所有未读通知为已读(XML 定义)。
     *
     * <p>P2-6: 增加 {@code batchSize} 参数实现分批 UPDATE，避免单次 UPDATE
     * 万级未读通知导致的长事务与行锁堆积。调用方需循环调用直到返回值 &lt; batchSize。
     *
     * @param userId    接收人 ID
     * @param batchSize 单批最大处理条数（&lt;= 0 时不限制，兼容旧逻辑）
     * @return 本批影响行数
     */
    int markAllRead(@Param("userId") String userId, @Param("batchSize") int batchSize);

    /**
     * 统计用户未读通知数(XML 定义)
     *
     * @param userId 接收人 ID
     * @return 未读数量
     */
    Long countUnread(@Param("userId") String userId);
}

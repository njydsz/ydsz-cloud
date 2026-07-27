package com.njydsz.message.server.service.impl.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.dto.config.SubscriptionUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgSubscription;
import com.njydsz.message.domain.enums.config.SubscriptionStatusEnum;
import com.njydsz.message.infra.mapper.config.MsgSubscriptionMapper;
import com.njydsz.message.server.service.config.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅关系服务实现。
 *
 * <p>按 (userId, topicCode, channel) upsert；退订更新状态为 UNSUBSCRIBED。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    /** 订阅关系 Mapper */
    private final MsgSubscriptionMapper msgSubscriptionMapper;

    /**
     * 新增或更新订阅关系
     *
     * <p>按 (userId, topicCode, channel) 唯一约束 upsert。新增时插入，已存在时更新状态。
     *
     * @param dto 订阅 upsert 参数
     * @return 落库后的订阅记录
     * @throws SysException 必填字段为空时抛出
     */
    @Override
    public MsgSubscription upsert(SubscriptionUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId())
                || !StringUtils.hasText(dto.getTopicCode()) || !StringUtils.hasText(dto.getChannel())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        MsgSubscription existing = msgSubscriptionMapper.selectOne(new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, dto.getUserId())
                .eq(MsgSubscription::getTopicCode, dto.getTopicCode())
                .eq(MsgSubscription::getChannel, dto.getChannel())
                .last("LIMIT 1"));
        String status = StringUtils.hasText(dto.getStatus()) ? dto.getStatus()
                : SubscriptionStatusEnum.SUBSCRIBED.name();
        if (existing == null) {
            MsgSubscription entity = new MsgSubscription();
            entity.setUserId(dto.getUserId());
            entity.setTopicCode(dto.getTopicCode());
            entity.setChannel(dto.getChannel());
            entity.setStatus(status);
            entity.setRoleScope(dto.getRoleScope());
            entity.setExtra(dto.getExtra());
            entity.setTenantId(TenantContext.getTenantId());
            msgSubscriptionMapper.insert(entity);
            log.info("[Subscription] 新建订阅: user={} topic={} channel={}", dto.getUserId(), dto.getTopicCode(), dto.getChannel());
            return entity;
        }
        existing.setStatus(status);
        existing.setRoleScope(dto.getRoleScope());
        existing.setExtra(dto.getExtra());
        // P1-5: 恢复订阅时清空退订时间;退订时记录退订时间
        if (SubscriptionStatusEnum.SUBSCRIBED.name().equals(status)) {
            existing.setUnsubscribedAt(null);
        } else if (SubscriptionStatusEnum.UNSUBSCRIBED.name().equals(status) && existing.getUnsubscribedAt() == null) {
            existing.setUnsubscribedAt(LocalDateTime.now());
        }
        msgSubscriptionMapper.updateById(existing);
        return existing;
    }

    /**
     * 查询指定用户的所有订阅记录
     *
     * @param userId 用户 ID
     * @return 订阅记录列表（按创建时间倒序）；userId 为空时返回空列表
     */
    @Override
    public List<MsgSubscription> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgSubscriptionMapper.selectList(new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, userId)
                .orderByDesc(MsgSubscription::getCreatedAt));
    }

    /**
     * 查询指定主题下的活跃订阅列表
     *
     * @param topicCode 主题编码
     * @param channel   消息通道（可空，空时查全部通道）
     * @return 订阅状态为 SUBSCRIBED 的记录列表
     */
    @Override
    public List<MsgSubscription> listByTopic(String topicCode, String channel) {
        if (!StringUtils.hasText(topicCode)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgSubscription> w = new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getTopicCode, topicCode)
                .eq(MsgSubscription::getStatus, SubscriptionStatusEnum.SUBSCRIBED.name());
        if (StringUtils.hasText(channel)) {
            w.eq(MsgSubscription::getChannel, channel);
        }
        return msgSubscriptionMapper.selectList(w);
    }

    /**
     * 判断用户是否已订阅指定主题与通道
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   消息通道（可空）
     * @return true 表示已订阅（SUBSCRIBED 状态）
     */
    @Override
    public boolean isSubscribed(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode)) {
            return false;
        }
        Long count = msgSubscriptionMapper.selectCount(new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, userId)
                .eq(MsgSubscription::getTopicCode, topicCode)
                .eq(StringUtils.hasText(channel), MsgSubscription::getChannel, channel)
                .eq(MsgSubscription::getStatus, SubscriptionStatusEnum.SUBSCRIBED.name()));
        return count != null && count > 0;
    }

    /**
     * 判断用户是否已退订指定主题与通道
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   消息通道（可空）
     * @return true 表示已退订（UNSUBSCRIBED 状态）
     */
    @Override
    public boolean isBlocked(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode)) {
            return false;
        }
        Long count = msgSubscriptionMapper.selectCount(new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, userId)
                .eq(MsgSubscription::getTopicCode, topicCode)
                .eq(StringUtils.hasText(channel), MsgSubscription::getChannel, channel)
                .eq(MsgSubscription::getStatus, SubscriptionStatusEnum.UNSUBSCRIBED.name()));
        return count != null && count > 0;
    }

    /**
     * 执行退订操作
     *
     * <p>将指定用户+主题+通道的订阅状态更新为 UNSUBSCRIBED。
     * 无记录时新建 UNSUBSCRIBED 记录（防止默认订阅语义下 isBlocked 返回 false）。
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   消息通道
     * @return 更新后的订阅记录
     * @throws SysException 必填字段为空时抛出
     */
    @Override
    public MsgSubscription unsubscribe(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode) || !StringUtils.hasText(channel)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        MsgSubscription existing = msgSubscriptionMapper.selectOne(new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, userId)
                .eq(MsgSubscription::getTopicCode, topicCode)
                .eq(MsgSubscription::getChannel, channel)
                .last("LIMIT 1"));
        if (existing == null) {
            // P1-5: 无订阅记录时也要创建 UNSUBSCRIBED 记录,否则 isBlocked 永远返回 false,
            // 用户点击退订后仍会被发送(默认订阅语义)。修复此 latent bug。
            MsgSubscription entity = new MsgSubscription();
            entity.setUserId(userId);
            entity.setTopicCode(topicCode);
            entity.setChannel(channel);
            entity.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
            entity.setUnsubscribedAt(LocalDateTime.now());
            entity.setTenantId(TenantContext.getTenantId());
            msgSubscriptionMapper.insert(entity);
            log.info("[Subscription] 退订(新建记录): user={} topic={} channel={}", userId, topicCode, channel);
            return entity;
        }
        existing.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
        existing.setUnsubscribedAt(LocalDateTime.now());
        msgSubscriptionMapper.updateById(existing);
        return existing;
    }
}

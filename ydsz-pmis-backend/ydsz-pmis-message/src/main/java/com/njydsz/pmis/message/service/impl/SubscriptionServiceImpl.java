package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.SubscriptionUpsertDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;
import com.njydsz.pmis.message.enums.SubscriptionStatusEnum;
import com.njydsz.pmis.message.mapper.MsgSubscriptionMapper;
import com.njydsz.pmis.message.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 订阅关系服务实现。
 *
 * <p>按 (userId, topicCode, channel) upsert；退订更新状态为 UNSUBSCRIBED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final MsgSubscriptionMapper msgSubscriptionMapper;

    @Override
    public MsgSubscriptionDO upsert(SubscriptionUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId())
                || !StringUtils.hasText(dto.getTopicCode()) || !StringUtils.hasText(dto.getChannel())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        MsgSubscriptionDO existing = msgSubscriptionMapper.selectOne(new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getUserId, dto.getUserId())
                .eq(MsgSubscriptionDO::getTopicCode, dto.getTopicCode())
                .eq(MsgSubscriptionDO::getChannel, dto.getChannel())
                .last("LIMIT 1"));
        String status = StringUtils.hasText(dto.getStatus()) ? dto.getStatus()
                : SubscriptionStatusEnum.SUBSCRIBED.name();
        if (existing == null) {
            MsgSubscriptionDO entity = new MsgSubscriptionDO();
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
        msgSubscriptionMapper.updateById(existing);
        return existing;
    }

    @Override
    public List<MsgSubscriptionDO> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgSubscriptionMapper.selectList(new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getUserId, userId)
                .orderByDesc(MsgSubscriptionDO::getCreatedAt));
    }

    @Override
    public List<MsgSubscriptionDO> listByTopic(String topicCode, String channel) {
        if (!StringUtils.hasText(topicCode)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgSubscriptionDO> w = new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getTopicCode, topicCode)
                .eq(MsgSubscriptionDO::getStatus, SubscriptionStatusEnum.SUBSCRIBED.name());
        if (StringUtils.hasText(channel)) {
            w.eq(MsgSubscriptionDO::getChannel, channel);
        }
        return msgSubscriptionMapper.selectList(w);
    }

    @Override
    public boolean isSubscribed(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode)) {
            return false;
        }
        Long count = msgSubscriptionMapper.selectCount(new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getUserId, userId)
                .eq(MsgSubscriptionDO::getTopicCode, topicCode)
                .eq(StringUtils.hasText(channel), MsgSubscriptionDO::getChannel, channel)
                .eq(MsgSubscriptionDO::getStatus, SubscriptionStatusEnum.SUBSCRIBED.name()));
        return count != null && count > 0;
    }

    @Override
    public boolean isBlocked(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode)) {
            return false;
        }
        Long count = msgSubscriptionMapper.selectCount(new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getUserId, userId)
                .eq(MsgSubscriptionDO::getTopicCode, topicCode)
                .eq(StringUtils.hasText(channel), MsgSubscriptionDO::getChannel, channel)
                .eq(MsgSubscriptionDO::getStatus, SubscriptionStatusEnum.UNSUBSCRIBED.name()));
        return count != null && count > 0;
    }

    @Override
    public void unsubscribe(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode) || !StringUtils.hasText(channel)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        MsgSubscriptionDO existing = msgSubscriptionMapper.selectOne(new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getUserId, userId)
                .eq(MsgSubscriptionDO::getTopicCode, topicCode)
                .eq(MsgSubscriptionDO::getChannel, channel)
                .last("LIMIT 1"));
        if (existing == null) {
            return;
        }
        existing.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
        msgSubscriptionMapper.updateById(existing);
    }
}

package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.dto.UnsubscribeQueryDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;
import com.njydsz.pmis.message.enums.SubscriptionStatusEnum;
import com.njydsz.pmis.message.mapper.MsgSubscriptionMapper;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.UnsubscribeService;
import com.njydsz.pmis.message.token.UnsubscribeTokenPayload;
import com.njydsz.pmis.message.token.UnsubscribeTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 退订中心服务实现（P1-5）。
 *
 * <p>编排 {@link UnsubscribeTokenUtil}（token 签名/校验）与 {@link SubscriptionService}
 * （订阅状态变更）。token 校验失败 / 过期 / 中心关闭均抛 {@link BizException}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnsubscribeServiceImpl implements UnsubscribeService {

    private final UnsubscribeTokenUtil unsubscribeTokenUtil;
    private final SubscriptionService subscriptionService;
    private final MsgSubscriptionMapper msgSubscriptionMapper;
    private final MessageProperties messageProperties;

    @Override
    public String generateToken(String userId, String topicCode, String channel) {
        return unsubscribeTokenUtil.generate(userId, topicCode, channel);
    }

    @Override
    public UnsubscribeTokenPayload previewToken(String token) {
        return unsubscribeTokenUtil.parseAndVerify(token);
    }

    @Override
    public MsgSubscriptionDO unsubscribeByToken(String token) {
        if (!messageProperties.getUnsubscribe().isEnabled()) {
            throw new BizException(BizErrorCode.BIZ_ERROR, "退订中心已关闭");
        }
        UnsubscribeTokenPayload payload = unsubscribeTokenUtil.parseAndVerify(token);
        log.info("[Unsubscribe] token 退订: user={} topic={} channel={}",
                payload.getUserId(), payload.getTopicCode(), payload.getChannel());
        return subscriptionService.unsubscribe(payload.getUserId(), payload.getTopicCode(), payload.getChannel());
    }

    @Override
    public PageResult<MsgSubscriptionDO> pageUnsubscribed(UnsubscribeQueryDTO query) {
        if (query == null) {
            query = new UnsubscribeQueryDTO();
        }
        Page<MsgSubscriptionDO> page = new Page<>(
                query.getPage(),
                Math.min(query.getSize(), PageQuery.MAX_SIZE));
        LambdaQueryWrapper<MsgSubscriptionDO> w = new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getStatus, SubscriptionStatusEnum.UNSUBSCRIBED.name())
                .eq(StringUtils.hasText(query.getUserId()), MsgSubscriptionDO::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getTopicCode()), MsgSubscriptionDO::getTopicCode, query.getTopicCode())
                .eq(StringUtils.hasText(query.getChannel()), MsgSubscriptionDO::getChannel, query.getChannel())
                .eq(StringUtils.hasText(query.getTenantId()), MsgSubscriptionDO::getTenantId, query.getTenantId())
                .orderByDesc(MsgSubscriptionDO::getUnsubscribedAt);
        Page<MsgSubscriptionDO> result = msgSubscriptionMapper.selectPage(page, w);
        return PageResult.ofPage(result);
    }

    @Override
    public void resubscribe(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode) || !StringUtils.hasText(channel)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        MsgSubscriptionDO existing = msgSubscriptionMapper.selectOne(new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getUserId, userId)
                .eq(MsgSubscriptionDO::getTopicCode, topicCode)
                .eq(MsgSubscriptionDO::getChannel, channel)
                .last("LIMIT 1"));
        if (existing == null) {
            // 无记录时直接新建 SUBSCRIBED 记录
            MsgSubscriptionDO entity = new MsgSubscriptionDO();
            entity.setUserId(userId);
            entity.setTopicCode(topicCode);
            entity.setChannel(channel);
            entity.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
            msgSubscriptionMapper.insert(entity);
            log.info("[Unsubscribe] 恢复订阅(新建): user={} topic={} channel={}", userId, topicCode, channel);
            return;
        }
        if (SubscriptionStatusEnum.SUBSCRIBED.name().equals(existing.getStatus())) {
            return;
        }
        existing.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
        existing.setUnsubscribedAt(null);
        msgSubscriptionMapper.updateById(existing);
        log.info("[Unsubscribe] 恢复订阅: user={} topic={} channel={}", userId, topicCode, channel);
    }
}

package com.remisoft.message.server.service.impl.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.core.response.PageResponse;
import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.message.domain.dto.config.UnsubscribeQueryDTO;
import com.remisoft.message.domain.entity.config.MsgSubscription;
import com.remisoft.message.domain.enums.config.SubscriptionStatusEnum;
import com.remisoft.message.infra.mapper.config.MsgSubscriptionMapper;
import com.remisoft.message.server.config.MessageProperties;
import com.remisoft.message.server.service.config.SubscriptionService;
import com.remisoft.message.server.service.config.UnsubscribeService;
import com.remisoft.message.server.token.UnsubscribeTokenPayload;
import com.remisoft.message.server.token.UnsubscribeTokenUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 退订服务实现。
 *
 * <p>管理用户对模板/渠道/标签的退订关系：URL 退订（一键退订链接）、
 *
 * <p>回复关键字退订（短信）、IM 退订。
 *
 * <p>退订记录作为通知偏好的「强约束」覆盖，发送前自动过滤。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class UnsubscribeServiceImpl implements UnsubscribeService {

    /** 退订 token 工具（签名/校验） */
    private final UnsubscribeTokenUtil unsubscribeTokenUtil;
    /** 订阅关系服务（状态变更） */
    private final SubscriptionService subscriptionService;
    /** 订阅关系 Mapper（退订查询） */
    private final MsgSubscriptionMapper msgSubscriptionMapper;
    /** 消息模块配置属性 */
    private final MessageProperties messageProperties;

    /**
     * 生成退订 token
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   消息通道
     * @return 签名后的退订 token
     */
    @Override
    public String generateToken(String userId, String topicCode, String channel) {
        return unsubscribeTokenUtil.generate(userId, topicCode, channel);
    }

    /**
     * 预览退订 token 信息（不执行退订）
     *
     * @param token 退订 token
     * @return token 载荷（userId、topicCode、channel、过期时间）
     */
    @Override
    public UnsubscribeTokenPayload previewToken(String token) {
        return unsubscribeTokenUtil.parseAndVerify(token);
    }

    /**
     * 通过退订 token 执行退订
     *
     * <p>校验 token 签名与有效期后，调用 SubscriptionService 更新订阅状态为 UNSUBSCRIBED。
     *
     * @param token 退订 token
     * @return 更新后的订阅记录
     * @throws SysException 退订中心关闭或 token 无效时抛出
     */
    @Override
    public MsgSubscription unsubscribeByToken(String token) {
        if (!messageProperties.getUnsubscribe().isEnabled()) {
            throw new SysException(BaseResultCode.BIZ_ERROR, "退订中心已关闭");
        }
        UnsubscribeTokenPayload payload = unsubscribeTokenUtil.parseAndVerify(token);
        log.info("[Unsubscribe] token 退订: user={} topic={} channel={}",
                payload.getUserId(), payload.getTopicCode(), payload.getChannel());
        return subscriptionService.unsubscribe(payload.getUserId(), payload.getTopicCode(), payload.getChannel());
    }

    /**
     * 分页查询已退订的订阅记录
     *
     * @param query 查询条件（userId、topicCode、channel、tenantId）
     * @return 分页结果
     */
    @Override
    public PageResponse<List<MsgSubscription>> pageUnsubscribed(UnsubscribeQueryDTO query) {
        if (query == null) {
            query = new UnsubscribeQueryDTO();
        }
        Page<MsgSubscription> page = new Page<>(
                query.getPageNum(),
                Math.min(query.getPageSize(), PageConstants.getMaxPageSize()));
        LambdaQueryWrapper<MsgSubscription> w = new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getStatus, SubscriptionStatusEnum.UNSUBSCRIBED.name())
                .eq(StringUtils.hasText(query.getUserId()), MsgSubscription::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getTopicCode()), MsgSubscription::getTopicCode, query.getTopicCode())
                .eq(StringUtils.hasText(query.getChannel()), MsgSubscription::getChannel, query.getChannel())
                .eq(StringUtils.hasText(query.getTenantId()), MsgSubscription::getTenantId, query.getTenantId())
                .orderByDesc(MsgSubscription::getUnsubscribedAt);
        Page<MsgSubscription> result = msgSubscriptionMapper.selectPage(page, w);
        return PageResponse.success(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /**
     * 恢复订阅
     *
     * <p>将指定用户+主题+通道的订阅状态恢复为 SUBSCRIBED。
     * 无记录时新建 SUBSCRIBED 记录；已订阅则跳过。
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   消息通道
     * @throws SysException 参数为空时抛出
     */
    @Override
    public void resubscribe(String userId, String topicCode, String channel) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode) || !StringUtils.hasText(channel)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        MsgSubscription existing = msgSubscriptionMapper.selectOne(new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, userId)
                .eq(MsgSubscription::getTopicCode, topicCode)
                .eq(MsgSubscription::getChannel, channel)
                .last("LIMIT 1"));
        if (existing == null) {
            // 无记录时直接新建 SUBSCRIBED 记录
            MsgSubscription entity = new MsgSubscription();
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

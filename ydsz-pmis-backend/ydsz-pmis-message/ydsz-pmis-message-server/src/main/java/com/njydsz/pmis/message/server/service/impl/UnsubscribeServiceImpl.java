package com.njydsz.pmis.message.server.service.impl.config;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.constant.PageConstants;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.message.domain.dto.config.UnsubscribeQueryDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgSubscriptionDO;
import com.njydsz.pmis.message.domain.enums.config.SubscriptionStatusEnum;
import com.njydsz.pmis.message.infra.mapper.config.MsgSubscriptionMapper;
import com.njydsz.pmis.message.server.config.MessageProperties;
import com.njydsz.pmis.message.server.service.config.SubscriptionService;
import com.njydsz.pmis.message.server.service.config.UnsubscribeService;
import com.njydsz.pmis.message.server.token.UnsubscribeTokenPayload;
import com.njydsz.pmis.message.server.token.UnsubscribeTokenUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 退订中心服务实现（P1-5）。
 *
 * <p>编排 {@link UnsubscribeTokenUtil}（token 签名/校验）与 {@link SubscriptionService}
 * （订阅状态变更）。token 校验失败 / 过期 / 中心关闭均抛 {@link SysException}。
 *
 * @author ydsz-pmis-team
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
    public MsgSubscriptionDO unsubscribeByToken(String token) {
        if (!messageProperties.getUnsubscribe().isEnabled()) {
            throw new SysException(StandardResultCode.BIZ_ERROR, "退订中心已关闭");
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
    public PageResponse<MsgSubscriptionDO> pageUnsubscribed(UnsubscribeQueryDTO query) {
        if (query == null) {
            query = new UnsubscribeQueryDTO();
        }
        Page<MsgSubscriptionDO> page = new Page<>(
                query.getPageNum(),
                Math.min(query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        LambdaQueryWrapper<MsgSubscriptionDO> w = new LambdaQueryWrapper<MsgSubscriptionDO>()
                .eq(MsgSubscriptionDO::getStatus, SubscriptionStatusEnum.UNSUBSCRIBED.name())
                .eq(StringUtils.hasText(query.getUserId()), MsgSubscriptionDO::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getTopicCode()), MsgSubscriptionDO::getTopicCode, query.getTopicCode())
                .eq(StringUtils.hasText(query.getChannel()), MsgSubscriptionDO::getChannel, query.getChannel())
                .eq(StringUtils.hasText(query.getTenantId()), MsgSubscriptionDO::getTenantId, query.getTenantId())
                .orderByDesc(MsgSubscriptionDO::getUnsubscribedAt);
        Page<MsgSubscriptionDO> result = msgSubscriptionMapper.selectPage(page, w);
        return PageResponse.ofPage(result);
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
            throw new SysException(StandardResultCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
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

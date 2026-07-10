package com.njydsz.pmis.message.service.impl.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.constant.SystemConstants;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.core.NotificationQueryDTO;
import com.njydsz.pmis.message.dto.core.NotificationSendDTO;
import com.njydsz.pmis.message.entity.core.MsgNotificationDO;
import com.njydsz.pmis.message.enums.receipt.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.core.MsgNotificationMapper;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.core.NotificationService;
import com.njydsz.pmis.message.service.receipt.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 站内通知服务实现。
 *
 * <p>send 支持批量接收人(receiverIds 优先),逐人入库 + 实时推送;撤回委托 {@link RecallService}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 站内通知 Mapper */
    private final MsgNotificationMapper msgNotificationMapper;
    /** 实时推送服务（WebSocket / 离线缓存） */
    private final RealtimePushService realtimePushService;
    /** 消息撤回服务 */
    private final RecallService recallService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int send(NotificationSendDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "通知参数不能为空");
        }
        List<String> receiverIds = resolveReceiverIds(dto);
        int count = 0;
        for (String rid : receiverIds) {
            MsgNotificationDO entity = buildEntity(dto, rid);
            msgNotificationMapper.insert(entity);
            // 实时推送（P0-4: 离线时自动缓存到 Redis，上线时补偿）
            realtimePushService.pushToUserWithOffline(rid, "NOTIFICATION", entity);
            count++;
        }
        log.info("[Notification] 发送通知: title={} count={} bizType={}", dto.getTitle(), count, dto.getBizType());
        return count;
    }

    @Override
    public Page<MsgNotificationDO> inbox(String userId, NotificationQueryDTO query) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID 不能为空");
        }
        Page<MsgNotificationDO> page = new Page<>(
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
        LambdaQueryWrapper<MsgNotificationDO> w = new LambdaQueryWrapper<MsgNotificationDO>()
                .eq(MsgNotificationDO::getReceiverId, userId);
        if (query != null) {
            w.eq(StringUtils.hasText(query.getCategory()), MsgNotificationDO::getCategory, query.getCategory());
            w.eq(StringUtils.hasText(query.getLevel()), MsgNotificationDO::getLevel, query.getLevel());
            w.eq(query.getReadStatus() != null, MsgNotificationDO::getReadStatus, query.getReadStatus());
        }
        w.orderByDesc(MsgNotificationDO::getCreatedAt);
        return msgNotificationMapper.selectPage(page, w);
    }

    @Override
    public long countUnread(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        Long count = msgNotificationMapper.countUnread(userId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean markRead(String userId, String id) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(id)) {
            return false;
        }
        return msgNotificationMapper.markRead(id, userId) > 0;
    }

    @Override
    public int markAllRead(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        return msgNotificationMapper.markAllRead(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, List<String> ids) {
        if (!StringUtils.hasText(userId) || CollectionUtils.isEmpty(ids)) {
            return;
        }
        for (String id : ids) {
            MsgNotificationDO n = msgNotificationMapper.selectById(id);
            if (n != null && userId.equals(n.getReceiverId())) {
                msgNotificationMapper.deleteById(id);
            }
        }
    }

    @Override
    public boolean recall(String userId, String id) {
        return recallService.recallNotification(userId, id);
    }

    private List<String> resolveReceiverIds(NotificationSendDTO dto) {
        List<String> receiverIds = dto.getReceiverIds();
        if (CollectionUtils.isEmpty(receiverIds) && dto.getReceiverId() != null) {
            receiverIds = List.of(dto.getReceiverId());
        }
        if (CollectionUtils.isEmpty(receiverIds)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "接收人不能为空");
        }
        return receiverIds;
    }

    private MsgNotificationDO buildEntity(NotificationSendDTO dto, String receiverId) {
        MsgNotificationDO n = new MsgNotificationDO();
        n.setTitle(dto.getTitle());
        n.setContent(dto.getContent());
        n.setLevel(StringUtils.hasText(dto.getLevel()) ? dto.getLevel() : "INFO");
        n.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : "SYSTEM");
        n.setPriority(dto.getPriority());
        n.setSenderId(StringUtils.hasText(dto.getSenderId()) ? dto.getSenderId() : SystemConstants.SYSTEM_USER_ID);
        n.setReceiverId(receiverId);
        n.setBizType(dto.getBizType());
        n.setBizId(dto.getBizId());
        n.setMessageGroup(dto.getMessageGroup());
        n.setActionUrl(dto.getActionUrl());
        n.setActionText(dto.getActionText());
        n.setIcon(dto.getIcon());
        n.setExtra(dto.getExtra());
        n.setSourceModule(dto.getSourceModule());
        n.setReadStatus(0);
        n.setRecallStatus(RecallStatusEnum.NONE.name());
        n.setExpiredAt(dto.getExpiredAt());
        // P2-7: 补齐租户隔离,与其他消息实体一致(原依赖 DB DEFAULT '1',多租户场景会越权)
        n.setTenantId(TenantContext.getTenantId());
        return n;
    }
}

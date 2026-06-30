package com.njydsz.pmis.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.notification.dto.NotificationQueryDTO;
import com.njydsz.pmis.notification.dto.NotificationSendDTO;
import com.njydsz.pmis.notification.entity.NotificationDO;
import com.njydsz.pmis.notification.mapper.NotificationMapper;
import com.njydsz.pmis.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int send(NotificationSendDTO dto) {
        List<Long> receiverIds = dto.getReceiverIds();
        if (CollectionUtils.isEmpty(receiverIds) && dto.getReceiverId() != null) {
            receiverIds = List.of(dto.getReceiverId());
        }
        if (CollectionUtils.isEmpty(receiverIds)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "接收人不能为空");
        }
        int count = 0;
        for (Long rid : receiverIds) {
            NotificationDO n = new NotificationDO();
            n.setTitle(dto.getTitle());
            n.setContent(dto.getContent());
            n.setLevel(dto.getLevel() == null ? "INFO" : dto.getLevel());
            n.setCategory(dto.getCategory() == null ? "SYSTEM" : dto.getCategory());
            n.setSenderId(dto.getSenderId());
            n.setReceiverId(rid);
            n.setBizType(dto.getBizType());
            n.setBizId(dto.getBizId());
            n.setReadStatus(0);
            n.setExpiredAt(dto.getExpiredAt());
            notificationMapper.insert(n);
            count++;
        }
        log.info("[Notification] 发送通知: title={} count={} bizType={}", dto.getTitle(), count, dto.getBizType());
        return count;
    }

    @Override
    public Page<NotificationDO> inbox(Long userId, NotificationQueryDTO query) {
        Page<NotificationDO> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<NotificationDO> w = new LambdaQueryWrapper<>();
        w.eq(NotificationDO::getReceiverId, userId);
        if (query.getCategory() != null) {
            w.eq(NotificationDO::getCategory, query.getCategory());
        }
        if (query.getLevel() != null) {
            w.eq(NotificationDO::getLevel, query.getLevel());
        }
        if (query.getReadStatus() != null) {
            w.eq(NotificationDO::getReadStatus, query.getReadStatus());
        }
        w.orderByDesc(NotificationDO::getId);
        return notificationMapper.selectPage(page, w);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getReceiverId, userId)
                .eq(NotificationDO::getReadStatus, 0));
    }

    @Override
    public boolean markRead(Long userId, Long id) {
        return notificationMapper.markRead(id, userId) > 0;
    }

    @Override
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        for (Long id : ids) {
            NotificationDO n = notificationMapper.selectById(id);
            if (n != null && n.getReceiverId().equals(userId)) {
                notificationMapper.deleteById(id);
            }
        }
    }
}

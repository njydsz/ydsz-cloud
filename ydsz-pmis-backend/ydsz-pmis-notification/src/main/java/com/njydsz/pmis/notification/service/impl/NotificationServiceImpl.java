package com.njydsz.pmis.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.notification.dto.NotificationQueryDTO;
import com.njydsz.pmis.notification.dto.NotificationSendDTO;
import com.njydsz.pmis.notification.entity.NotificationDO;
import com.njydsz.pmis.notification.feign.MessageServiceClient;
import com.njydsz.pmis.notification.feign.UserServiceClient;
import com.njydsz.pmis.notification.mapper.NotificationMapper;
import com.njydsz.pmis.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

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
    private final MessageServiceClient messageServiceClient;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int send(NotificationSendDTO dto) {
        List<Long> receiverIds = resolveReceiverIds(dto);
        int count = 0;
        for (Long rid : receiverIds) {
            notificationMapper.insert(buildEntity(dto, rid));
            count++;
        }
        log.info("[Notification] 发送通知: title={} count={} bizType={}", dto.getTitle(), count, dto.getBizType());
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmailDispatchResult sendWithEmail(NotificationSendDTO dto) {
        // 1. 站内通知入库（仅支持单接收人）
        if (dto.getReceiverId() == null
                && (dto.getReceiverIds() == null || dto.getReceiverIds().size() != 1)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "邮件通知仅支持单接收人");
        }
        int count = send(dto);

        // 2. 邮件投递
        EmailDispatchResult result = new EmailDispatchResult();
        result.setInboxCount(count);

        if (!Boolean.TRUE.equals(dto.getEmailEnabled())) {
            return result;
        }

        String email = resolveReceiverEmail(dto);
        if (!StringUtils.hasText(email)) {
            log.warn("[Notification] 邮件投递跳过: 接收人邮箱为空 receiverId={}", dto.getReceiverId());
            result.setEmailSent(false);
            result.setEmailError("接收人邮箱为空");
            return result;
        }

        try {
            MessageServiceClient.MessageFeignDTO msg = new MessageServiceClient.MessageFeignDTO();
            msg.setChannel("EMAIL");
            msg.setReceiver(email);
            msg.setSubject(dto.getTitle());
            msg.setContent(buildHtmlContent(dto));
            msg.setBizType(dto.getBizType() == null ? "NOTIF" : dto.getBizType());
            msg.setBizId(dto.getBizId());
            R<Object> resp = messageServiceClient.send(msg);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                result.setEmailSent(true);
                Object trace = extractTraceId(resp.getData());
                if (trace != null) {
                    result.setProviderTraceId(String.valueOf(trace));
                }
                log.info("[Notification] 邮件投递成功: receiverId={} email={} traceId={}",
                        dto.getReceiverId(), email, result.getProviderTraceId());
            } else {
                String err = resp == null ? "消息服务无响应" : ("code=" + resp.getCode() + " msg=" + resp.getMessage());
                result.setEmailSent(false);
                result.setEmailError(err);
                log.warn("[Notification] 邮件投递失败: receiverId={} email={} reason={}",
                        dto.getReceiverId(), email, err);
            }
        } catch (Exception e) {
            result.setEmailSent(false);
            result.setEmailError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("[Notification] 邮件投递异常: receiverId={} reason={}", dto.getReceiverId(), e.getMessage(), e);
        }
        return result;
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
        Long count = notificationMapper.countUnread(userId);
        return count == null ? 0L : count;
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

    // ==================== 内部 ====================

    private List<Long> resolveReceiverIds(NotificationSendDTO dto) {
        List<Long> receiverIds = dto.getReceiverIds();
        if (CollectionUtils.isEmpty(receiverIds) && dto.getReceiverId() != null) {
            receiverIds = List.of(dto.getReceiverId());
        }
        if (CollectionUtils.isEmpty(receiverIds)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "接收人不能为空");
        }
        return receiverIds;
    }

    private NotificationDO buildEntity(NotificationSendDTO dto, Long receiverId) {
        NotificationDO n = new NotificationDO();
        n.setTitle(dto.getTitle());
        n.setContent(dto.getContent());
        n.setLevel(dto.getLevel() == null ? "INFO" : dto.getLevel());
        n.setCategory(dto.getCategory() == null ? "SYSTEM" : dto.getCategory());
        n.setSenderId(dto.getSenderId());
        n.setReceiverId(receiverId);
        n.setBizType(dto.getBizType());
        n.setBizId(dto.getBizId());
        n.setReadStatus(0);
        n.setExpiredAt(dto.getExpiredAt());
        return n;
    }

    private String resolveReceiverEmail(NotificationSendDTO dto) {
        if (StringUtils.hasText(dto.getReceiverEmail())) {
            return dto.getReceiverEmail().trim();
        }
        Long uid = dto.getReceiverId();
        if (uid == null && dto.getReceiverIds() != null && dto.getReceiverIds().size() == 1) {
            uid = dto.getReceiverIds().get(0);
        }
        if (uid == null) {
            return null;
        }
        try {
            R<Map<String, Object>> resp = userServiceClient.getEmployee(uid);
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                return null;
            }
            Object email = resp.getData().get("email");
            return email == null ? null : String.valueOf(email);
        } catch (Exception e) {
            log.warn("[Notification] 获取接收人邮箱失败: receiverId={} reason={}", uid, e.getMessage());
            return null;
        }
    }

    private String buildHtmlContent(NotificationSendDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Microsoft YaHei,Arial,sans-serif;line-height:1.6;color:#333;\">");
        sb.append("<h3 style=\"margin:0 0 12px 0;\">").append(escapeHtml(dto.getTitle())).append("</h3>");
        if (StringUtils.hasText(dto.getContent())) {
            sb.append("<div>").append(escapeHtml(dto.getContent())).append("</div>");
        }
        sb.append("<div style=\"margin-top:16px;color:#999;font-size:12px;\">本邮件由 PMIS 运营管理系统自动发出，请勿直接回复。</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @SuppressWarnings("unchecked")
    private Object extractTraceId(Object data) {
        if (data == null) return null;
        if (data instanceof Map) {
            Object t = ((Map<String, Object>) data).get("providerTraceId");
            if (t != null) return t;
            return ((Map<String, Object>) data).get("traceId");
        }
        return null;
    }
}

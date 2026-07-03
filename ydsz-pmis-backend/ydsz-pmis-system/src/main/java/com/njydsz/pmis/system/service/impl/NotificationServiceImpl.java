package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.system.dto.NotificationQueryDTO;
import com.njydsz.pmis.system.dto.NotificationSendDTO;
import com.njydsz.pmis.system.entity.NotificationDO;
import com.njydsz.pmis.system.feign.MessageServiceClient;
import com.njydsz.pmis.system.feign.UserServiceClient;
import com.njydsz.pmis.system.mapper.NotificationMapper;
import com.njydsz.pmis.system.service.NotificationService;
import com.njydsz.pmis.system.service.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.apache.seata.spring.annotation.GlobalTransactional;

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

    /** 通知 Mapper */
    private final NotificationMapper notificationMapper;
    /** 消息服务 Feign 客户端 */
    private final MessageServiceClient messageServiceClient;
    /** 用户服务 Feign 客户端 */
    private final UserServiceClient userServiceClient;
    /** 实时推送服务（WebSocket，P0-2） */
    private final RealtimePushService realtimePushService;

    /**
     * 发送通知（支持单接收/批量）
     *
     * @param dto 通知发送表单
     * @return 实际插入条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int send(NotificationSendDTO dto) {
        List<Long> receiverIds = resolveReceiverIds(dto);
        int count = 0;
        for (Long rid : receiverIds) {
            NotificationDO notificationDO = buildEntity(dto, rid);
            notificationMapper.insert(notificationDO);
            // P0-2: 实时推送通知到接收人（推送失败不影响主流程，由 RealtimePushService 内部降级）
            realtimePushService.pushToUser(rid, "NOTIFICATION", notificationDO);
            count++;
        }
        log.info("[Notification] 发送通知: title={} count={} bizType={}", dto.getTitle(), count, dto.getBizType());
        return count;
    }

    /**
     * 发送通知 + 邮件投递（仅单接收人）
     *
     * @param dto 通知发送表单（仅支持单接收人）
     * @return EmailDispatchResult 邮件投递结果（成功/失败/降级）
     * @throws BizException 当接收人非单人时抛出
     */
    @Override
    @GlobalTransactional(name = "pmis-notification-send-with-email", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public EmailDispatchResult sendWithEmail(NotificationSendDTO dto) {
        // 1. 站内通知入库（仅支持单接收人）
        if (dto.getReceiverId() == null
                && (dto.getReceiverIds() == null || dto.getReceiverIds().size() != 1)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.common.msg_1bd45dc9");
        }
        int count = send(dto);

        // 2. 邮件投递
        EmailDispatchResult R = new EmailDispatchResult();
        R.setInboxCount(count);

        if (!Boolean.TRUE.equals(dto.getEmailEnabled())) {
            return R;
        }

        String email = resolveReceiverEmail(dto);
        if (!StringUtils.hasText(email)) {
            log.warn("[Notification] 邮件投递跳过: 接收人邮箱为空 receiverId={}", dto.getReceiverId());
            R.setEmailSent(false);
            R.setEmailError("接收人邮箱为空");
            return R;
        }

        try {
            MessageServiceClient.MessageFeignDTO msg = new MessageServiceClient.MessageFeignDTO();
            msg.setChannel("EMAIL");
            msg.setReceiver(email);
            msg.setSubject(dto.getTitle());
            msg.setContent(buildHtmlContent(dto));
            msg.setBizType(dto.getBizType() == null ? "NOTIF" : dto.getBizType());
            msg.setBizId(dto.getBizId());
            Result<Object> resp = messageServiceClient.send(msg);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                R.setEmailSent(true);
                Object trace = extractTraceId(resp.getData());
                if (trace != null) {
                    R.setProviderTraceId(String.valueOf(trace));
                }
                log.info("[Notification] 邮件投递成功: receiverId={} email={} traceId={}",
                        dto.getReceiverId(), email, R.getProviderTraceId());
            } else {
                String err = resp == null ? "消息服务无响应" : ("code=" + resp.getCode() + " msg=" + resp.getMessage());
                R.setEmailSent(false);
                R.setEmailError(err);
                log.warn("[Notification] 邮件投递失败: receiverId={} email={} reason={}",
                        dto.getReceiverId(), email, err);
            }
        } catch (Exception e) {
            R.setEmailSent(false);
            R.setEmailError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("[Notification] 邮件投递异常: receiverId={} reason={}", dto.getReceiverId(), e.getMessage(), e);
        }
        // P0-2: 实时推送（含邮件投递结果）到接收人，失败不影响主流程
        Long pushUserId = dto.getReceiverId() != null ? dto.getReceiverId()
                : (dto.getReceiverIds() != null && !dto.getReceiverIds().isEmpty()
                        ? dto.getReceiverIds().get(0) : null);
        if (pushUserId != null) {
            realtimePushService.pushToUser(pushUserId, "NOTIFICATION", R);
        }
        return R;
    }

    /**
     * 收件箱分页查询，支持按分类/级别/已读状态过滤。
     *
     * @param userId 接收人 ID
     * @param query  查询条件
     * @return 通知分页结果
     */
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

    /**
     * 未读数量
     *
     * @param userId 接收人 ID
     * @return 未读通知数
     */
    @Override
    public long countUnread(Long userId) {
        Long count = notificationMapper.countUnread(userId);
        return count == null ? 0L : count;
    }

    /**
     * 标记已读
     *
     * @param userId 接收人 ID
     * @param id     通知 ID
     * @return 是否标记成功（通知不存在或不属于该用户时返回 false）
     */
    @Override
    public boolean markRead(Long userId, Long id) {
        return notificationMapper.markRead(id, userId) > 0;
    }

    /**
     * 全部标记已读
     *
     * @param userId 接收人 ID
     * @return 实际标记条数
     */
    @Override
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }

    /**
     * 删除通知（逻辑删除，仅允许删除属于自己的通知）
     *
     * @param userId 接收人 ID
     * @param ids    通知 ID 列表
     */
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

    /**
     * 解析接收人 ID 列表（优先 receiverIds，其次 receiverId）
     *
     * @param dto 通知发送表单
     * @return 接收人 ID 列表
     * @throws BizException 当接收人为空时抛出
     */
    private List<Long> resolveReceiverIds(NotificationSendDTO dto) {
        List<Long> receiverIds = dto.getReceiverIds();
        if (CollectionUtils.isEmpty(receiverIds) && dto.getReceiverId() != null) {
            receiverIds = List.of(dto.getReceiverId());
        }
        if (CollectionUtils.isEmpty(receiverIds)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.common.msg_35f5875c");
        }
        return receiverIds;
    }

    /**
     * 根据发送表单和接收人 ID 构建通知实体。
     *
     * @param dto         通知发送表单
     * @param receiverId  接收人 ID
     * @return 通知实体
     */
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

    /**
     * 解析接收人邮箱：优先使用表单中显式填写的邮箱，否则通过 Feign 查询用户服务获取。
     *
     * @param dto 通知发送表单
     * @return 邮箱地址，无则返回 null
     */
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
            Result<Map<String, Object>> resp = userServiceClient.getEmployee(uid);
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

    /**
     * 构建邮件 HTML 内容
     *
     * @param dto 通知发送表单
     * @return HTML 内容字符串
     */
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

    /**
     * HTML 特殊字符转义
     *
     * @param input 原始字符串
     * @return 转义后的字符串
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 从响应数据中提取供应商追踪 ID
     *
     * @param data 响应数据
     * @return 追踪 ID，无则返回 null
     */
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

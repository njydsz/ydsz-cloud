package com.njydsz.pmis.workflow.service.impl.notification;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.entity.notification.FlowMentionDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowMentionMapper;
import com.njydsz.pmis.workflow.service.notification.FlowMentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审批 @提及服务实现（P2-3）
 *
 * <p>解析评论文本中的 @{userId} 标记，为每个被提及用户创建记录并推送通知。
 *
 * <p>标记格式：@{userId} 或 @[userId] 或 @userId
 * 推荐使用 @{userId} 格式，避免与普通文本中的 @ 符号冲突。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowMentionServiceImpl implements FlowMentionService {

    private final FlowMentionMapper mentionMapper;
    private final FlowNotificationHelper notificationHelper;

    /** @{userId} 格式的提及标记正则 */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\{([^}]+)\\}");

    /** 最多处理的提及数量（防止滥用） */
    private static final int MAX_MENTIONS_PER_COMMENT = 20;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> processMentions(String instanceId, String taskId, String comment,
                                         String mentionedBy, String tenantId) {
        if (!StringUtils.hasText(comment)) {
            return List.of();
        }

        // 解析 @userId 标记
        List<String> mentionedUserIds = extractMentions(comment);
        if (mentionedUserIds.isEmpty()) {
            return List.of();
        }

        // 限制单次提及数量
        if (mentionedUserIds.size() > MAX_MENTIONS_PER_COMMENT) {
            log.warn("[Mention] 提及数量超限，截断: original={} max={}",
                    mentionedUserIds.size(), MAX_MENTIONS_PER_COMMENT);
            mentionedUserIds = mentionedUserIds.subList(0, MAX_MENTIONS_PER_COMMENT);
        }

        List<String> processed = new ArrayList<>();
        for (String userId : mentionedUserIds) {
            // 不提及自己
            if (userId.equals(mentionedBy)) {
                continue;
            }

            FlowMentionDO mention = new FlowMentionDO();
            mention.setInstanceId(instanceId);
            mention.setTaskId(taskId);
            mention.setMentionedBy(mentionedBy);
            mention.setMentionedUserId(userId);
            mention.setComment(comment);
            mention.setReadStatus(false);
            mention.setTenantId(tenantId);
            mentionMapper.insert(mention);

            // 推送通知
            try {
                String title = "您在审批中被 @提及";
                String content = String.format("有人在审批中提到了您，请查看详情。\n评论: %s",
                        truncate(comment, 100));
                notificationHelper.notifyUrge(List.of(userId), title, content, instanceId);
            } catch (Exception e) {
                log.warn("[Mention] 通知推送失败: userId={} err={}", userId, e.getMessage());
            }

            processed.add(userId);
        }

        log.info("[Mention] 处理提及完成: instanceId={} mentionedBy={} users={}",
                instanceId, mentionedBy, processed);
        return processed;
    }

    @Override
    public List<Map<String, Object>> listMentions(String userId, String tenantId, boolean onlyUnread) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return mentionMapper.selectMentionsForUser(userId, tenantId, onlyUnread);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String mentionId, String userId) {
        if (!StringUtils.hasText(mentionId) || !StringUtils.hasText(userId)) {
            return;
        }
        FlowMentionDO mention = mentionMapper.selectById(mentionId);
        if (mention == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "提及记录不存在: " + mentionId);
        }
        // 安全校验：只能标记自己的提及
        if (!userId.equals(mention.getMentionedUserId())) {
            throw new BizException(BizErrorCode.FORBIDDEN, "无权操作他人的提及记录");
        }
        mention.setReadStatus(true);
        mention.setReadAt(LocalDateTime.now());
        mentionMapper.updateById(mention);
    }

    @Override
    public long countUnread(String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        return mentionMapper.countUnread(userId, tenantId);
    }

    // ============================== 内部方法 ==============================

    /**
     * 从评论中提取 @userId 标记。
     *
     * @param comment 评论内容
     * @return 被提及的用户 ID 列表（去重）
     */
    private List<String> extractMentions(String comment) {
        List<String> userIds = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(comment);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (matcher.find()) {
            String userId = matcher.group(1).trim();
            if (!userId.isEmpty() && seen.add(userId)) {
                userIds.add(userId);
            }
        }
        return userIds;
    }

    /**
     * 截断字符串到指定长度。
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}

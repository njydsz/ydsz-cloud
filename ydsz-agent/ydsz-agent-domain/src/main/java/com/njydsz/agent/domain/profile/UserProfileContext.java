package com.njydsz.agent.domain.profile;

import java.util.List;

/**
 * 用户画像上下文（值对象）。
 *
 * <p>用于组装 System Prompt 的动态注入，将画像中的核心偏好信息提炼为
 * 轻量级上下文，在每次对话开始时自动注入到 LLM System Prompt 中。</p>
 *
 * <p>与 {@link UserProfile} 的区别：</p>
 * <ul>
 *   <li>{@code UserProfile} — 完整的持久化实体，包含所有统计信息</li>
 *   <li>{@code UserProfileContext} — 仅提取 System Prompt 所需的精简信息</li>
 * </ul>
 *
 * @param userId             用户 ID
 * @param preferredLanguage  偏好语言
 * @param interestedDomains  关注领域列表（Top N）
 * @param queryStyle         查询风格
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
public record UserProfileContext(
        String userId,
        String preferredLanguage,
        List<String> interestedDomains,
        String queryStyle) {

    /**
     * 判断画像上下文是否有效（包含有价值的信息）。
     *
     * <p>当所有字段都为空时，画像无实质性内容，无需注入 System Prompt。</p>
     *
     * @return true 表示画像包含有价值的信息
     */
    public boolean hasContent() {
        return (preferredLanguage != null && !preferredLanguage.isBlank())
                || (interestedDomains != null && !interestedDomains.isEmpty())
                || (queryStyle != null && !queryStyle.isBlank());
    }

    /**
     * 构建 System Prompt 画像注入文本。
     *
     * <p>格式化为可直接追加到 System Prompt 末尾的文本片段。
     * 当画像无内容时返回空字符串，调用方可安全追加。</p>
     *
     * @return System Prompt 注入文本片段；无内容时返回空字符串
     */
    public String toSystemPromptSegment() {
        if (!hasContent()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("\n\n[用户画像]\n");
        if (preferredLanguage != null && !preferredLanguage.isBlank()) {
            sb.append("偏好语言: ").append(preferredLanguage).append("\n");
        }
        if (interestedDomains != null && !interestedDomains.isEmpty()) {
            sb.append("关注领域: ").append(String.join(", ", interestedDomains)).append("\n");
        }
        if (queryStyle != null && !queryStyle.isBlank()) {
            sb.append("查询风格: ").append(queryStyle).append("\n");
        }
        return sb.toString();
    }
}

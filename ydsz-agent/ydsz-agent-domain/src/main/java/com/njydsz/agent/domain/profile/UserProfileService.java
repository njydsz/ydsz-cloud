package com.njydsz.agent.domain.profile;

import java.util.List;
import java.util.Optional;

import com.njydsz.agent.domain.memory.MemoryExtractedFact;
import com.njydsz.agent.domain.model.ChatMessage;

/**
 * 用户画像领域服务接口。
 *
 * <p>定义用户画像的核心操作：交互记录、画像构建、上下文获取、记忆刷新。
 * 由基础设施层和服务器层协作实现。</p>
 *
 * <p><b>生命周期：</b></p>
 * <ol>
 *   <li>首次交互时 {@link #buildInitialProfile} 创建默认画像</li>
 *   <li>每次交互时 {@link #recordInteraction} 更新频次/意图统计</li>
 *   <li>记忆整合后 {@link #refreshFromMemory} 从事实记忆刷新画像</li>
 *   <li>对话开始时 {@link #getContext} 获取画像上下文注入 System Prompt</li>
 * </ol>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
public interface UserProfileService {

    /**
     * 记录一次交互，更新频次/意图统计。
     *
     * <p>每次用户发送消息后调用，自动提取领域关键词和意图标签，
     * 更新对应字段的频次统计。若画像不存在则先初始化。</p>
     *
     * @param userId  用户 ID
     * @param message 本次对话消息
     */
    void recordInteraction(String userId, ChatMessage message);

    /**
     * 获取用户画像上下文（用于 System Prompt 注入）。
     *
     * <p>返回轻量级的 {@link UserProfileContext}，仅包含注入 System Prompt
     * 所需的核心偏好信息。若画像不存在返回 {@code Optional.empty()}。</p>
     *
     * @param userId 用户 ID
     * @return 画像上下文；不存在返回 {@code Optional.empty()}
     */
    Optional<UserProfileContext> getContext(String userId);

    /**
     * 首次接触时初始化用户画像。
     *
     * <p>为该用户创建默认的全局画像，设置初始值和基本信息。
     * 若画像已存在，不做任何操作。</p>
     *
     * @param userId 用户 ID
     */
    void buildInitialProfile(String userId);

    /**
     * 从事实记忆中刷新用户画像。
     *
     * <p>从 {@link MemoryExtractedFact} 列表统计领域分布和意图标签，
     * 更新画像中的关注领域、频次统计和查询风格。
     * 该方法由 {@code MemoryConsolidationService} 在记忆整合完成后调用。</p>
     *
     * @param userId 用户 ID
     * @param facts  该用户已提取的记忆事实列表
     */
    void refreshFromMemory(String userId, List<MemoryExtractedFact> facts);
}

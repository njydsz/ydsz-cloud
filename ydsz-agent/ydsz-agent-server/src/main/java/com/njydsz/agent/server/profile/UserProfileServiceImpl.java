package com.njydsz.agent.server.profile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.memory.MemoryExtractedFact;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.agent.domain.profile.UserProfile;
import com.njydsz.agent.domain.profile.UserProfileContext;
import com.njydsz.agent.domain.profile.UserProfileRepository;
import com.njydsz.agent.domain.profile.UserProfileService;

/**
 * 用户画像领域服务实现。
 *
 * <p>实现 {@link UserProfileService} 接口，负责：</p>
 * <ul>
 *   <li>交互记录 — 从用户消息中提取领域关键词和意图标签</li>
 *   <li>画像构建 — 首次接触时创建默认画像</li>
 *   <li>记忆刷新 — 从 MemoryExtractedFact 中统计数据并更新画像</li>
 *   <li>上下文获取 — 返回轻量级 UserProfileContext 供 System Prompt 注入</li>
 * </ul>
 *
 * <p>支持两种分析模式（由 {@code ydsz.agent.profile.llm-analysis-enabled} 控制）：</p>
 * <ul>
 *   <li>LLM 分析模式 — 更准确，但消耗额外 Token</li>
 *   <li>规则分析模式（默认）— 基于已知领域列表的关键词匹配</li>
 * </ul>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "ydsz.agent.profile",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UserProfileServiceImpl implements UserProfileService {

  /** 画像关注领域提取上限 */
  private static final int TOP_DOMAIN_LIMIT = 5;

    /** 集合初始容量 */
    private static final int COLLECTION_CAPACITY = 16;

    /** 默认偏好语言 */
    private static final String DEFAULT_LANGUAGE = "zh-CN";

    /** 已知领域关键词映射（领域 -> 触发关键词列表） */
    private static final Map<String, List<String>> DOMAIN_KEYWORDS;

    static {
        Map<String, List<String>> keywords = new LinkedHashMap<>(COLLECTION_CAPACITY);
        keywords.put("项目管理", List.of("项目", "project", "进度", "计划", "甘特图", "里程碑"));
        keywords.put("任务管理", List.of("任务", "task", "待办", "todolist", "checklist"));
        keywords.put("数据分析", List.of("数据", "报表", "分析", "统计", "dashboard", "图表", "KPI"));
        keywords.put("审批流程", List.of("审批", "审批流", "审核", "approval", "流程", "签字"));
        keywords.put("知识库", List.of("知识", "文档", "wiki", "知识库", "手册"));
        keywords.put("消息通讯", List.of("通知", "消息", "邮件", "推送", "提醒", "公告"));
        keywords.put("团队管理", List.of("团队", "人员", "组织", "架构", "权限", "角色"));
        keywords.put("系统管理", List.of("系统", "配置", "日志", "监控", "运维", "部署"));
        DOMAIN_KEYWORDS = Collections.unmodifiableMap(keywords);
    }

    /** 高频意图标签列表 */
    private static final List<String> KNOWN_INTENTS = List.of(
            "项目进度查询", "任务分配", "数据分析", "审批提交", "文档检索",
            "消息通知", "报表导出", "权限查询", "状态变更", "知识问答");

    private final UserProfileRepository userProfileRepository;
    private final AgentProperties agentProperties;

    /** LLM 画像分析器（可选组件） */
    private final LlmProfileAnalyzer llmProfileAnalyzer;

    public UserProfileServiceImpl(UserProfileRepository userProfileRepository,
                                  AgentProperties agentProperties,
                                  LlmProfileAnalyzer llmProfileAnalyzer) {
        this.userProfileRepository = userProfileRepository;
        this.agentProperties = agentProperties;
        this.llmProfileAnalyzer = llmProfileAnalyzer;
    }

    @Override
    public void recordInteraction(String userId, ChatMessage message) {
        if (userId == null || userId.isBlank() || message == null) {
            return;
        }

        try {
            Optional<UserProfile> existing = userProfileRepository.findByUserId(userId);
            UserProfile profile = existing.orElseGet(() -> createDefaultProfile(userId));

            // 更新总交互次数
            int currentInteractions = profile.getTotalInteractions() != null
                    ? profile.getTotalInteractions() : 0;
            profile.setTotalInteractions(currentInteractions + 1);
            profile.setLastInteractionAt(LocalDateTime.now());

            // 仅处理用户角色消息（分析用户输入模式）
            if (MessageRole.USER.equals(message.getRole())) {
                String content = message.getContent();
                if (content != null && !content.isBlank()) {
                    analyzeContent(profile, content);
                }
            }

            userProfileRepository.save(profile);
            log.debug("画像交互已记录: userId={}, interactions={}",
                    userId, profile.getTotalInteractions());
        } catch (Exception e) {
            log.warn("画像交互记录失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public Optional<UserProfileContext> getContext(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return userProfileRepository.findByUserId(userId)
                .map(this::toContext);
    }

    @Override
    public void buildInitialProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            Optional<UserProfile> existing = userProfileRepository.findByUserId(userId);
            if (existing.isPresent()) {
                log.debug("画像已存在，跳过初始化: userId={}", userId);
                return;
            }
            UserProfile profile = createDefaultProfile(userId);
            userProfileRepository.save(profile);
            log.info("用户画像已初始化: userId={}", userId);
        } catch (Exception e) {
            log.warn("画像初始化失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void refreshFromMemory(String userId, List<MemoryExtractedFact> facts) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            UserProfile profile = userProfileRepository.findByUserId(userId)
                    .orElseGet(() -> createDefaultProfile(userId));

            if (facts == null || facts.isEmpty()) {
                log.debug("无记忆事实，跳过画像刷新: userId={}", userId);
                return;
            }

            // 1. 统计事实中的领域分布
            Map<String, Integer> domainFromFacts = new HashMap<>(COLLECTION_CAPACITY);
            for (MemoryExtractedFact fact : facts) {
                if (fact.getCategory() != null) {
                    domainFromFacts.merge(fact.getCategory().trim(), 1, Integer::sum);
                }
            }

            // 2. 合并到画像的现有频次数据中
            Map<String, Integer> existingFrequency = new HashMap<>(profile.getDomainFrequency());
            domainFromFacts.forEach((domain, count) ->
                    existingFrequency.merge(domain, count, Integer::sum));
            profile.setDomainFrequency(existingFrequency);

            // 3. 更新关注领域（取频次最高的 top N）
            int topN = agentProperties.getMemory().getProfileTopDomains();
            List<String> sortedDomains = sortDomainsByFrequency(existingFrequency, topN);
            profile.setInterestedDomains(sortedDomains);

            // 4. 提取高频意图标签
            List<String> commonIntents = extractCommonIntents(facts);
            profile.setCommonIntents(commonIntents);

            // 5. LLM 分析模式：可选更新查询风格
            if (agentProperties.getMemory().isLlmAnalysisEnabled() && llmProfileAnalyzer != null) {
                try {
                    List<String> factContents = facts.stream()
                            .map(MemoryExtractedFact::getContent)
                            .filter(c -> c != null && !c.isBlank())
                            .toList();
                    String style = llmProfileAnalyzer.analyzeStyle(factContents);
                    if (style != null && !style.isBlank()) {
                        profile.setQueryStyle(style);
                    }
                } catch (Exception e) {
                    log.warn("LLM 风格分析失败（已跳过）: userId={}, error={}", userId, e.getMessage());
                }
            }

            userProfileRepository.save(profile);
            log.info("画像已从记忆刷新: userId={}, factsCount={}, domains={}",
                    userId, facts.size(), sortedDomains.size());
        } catch (Exception e) {
            log.warn("画像刷新失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 分析消息内容，提取领域关键词和意图标签。
     *
     * @param profile 用户画像（原地修改）
     * @param content 消息内容
     */
    private void analyzeContent(UserProfile profile, String content) {
        // 基于规则的领域关键词匹配
        for (Map.Entry<String, List<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (content.contains(keyword)) {
                    profile.incrementDomainFrequency(entry.getKey());
                    break;
                }
            }
        }
    }

    /**
     * 创建默认用户画像。
     *
     * @param userId 用户 ID
     * @return 默认画像实体
     */
    private UserProfile createDefaultProfile(String userId) {
        return UserProfile.builder()
                .userId(userId)
                .preferredLanguage(DEFAULT_LANGUAGE)
                .totalInteractions(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 将 UserProfile 转换为 UserProfileContext。
     *
     * @param profile 持久化画像实体
     * @return 轻量级上下文值对象
     */
    private UserProfileContext toContext(UserProfile profile) {
        int topN = agentProperties.getMemory().getProfileTopDomains();
        List<String> topDomains = profile.getTopDomains(topN);
        return new UserProfileContext(
                profile.getUserId(),
                profile.getPreferredLanguage(),
                topDomains,
                profile.getQueryStyle());
    }

    /**
     * 按频次降序排列领域，取 Top N。
     *
     * @param frequency 领域频次映射
     * @param topN      返回数量上限
     * @return Top N 领域列表
     */
    private List<String> sortDomainsByFrequency(Map<String, Integer> frequency, int topN) {
        if (frequency.isEmpty()) {
            return Collections.emptyList();
        }
        return frequency.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 从记忆事实中提取高频意图标签。
     *
     * <p>根据事实类别与已知意图的映射关系，匹配出现频率最高的意图标签。</p>
     *
     * @param facts 记忆事实列表
     * @return 高频意图标签列表（最多 5 个）
     */
    private List<String> extractCommonIntents(List<MemoryExtractedFact> facts) {
        Map<String, Integer> intentCount = new HashMap<>(COLLECTION_CAPACITY);
        for (MemoryExtractedFact fact : facts) {
            String category = fact.getCategory();
            if (category == null) {
                continue;
            }
            // 类别到意图标签的映射
            String intent = mapCategoryToIntent(category);
            if (intent != null) {
                intentCount.merge(intent, 1, Integer::sum);
            }
        }

        return intentCount.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(TOP_DOMAIN_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 将事实类别映射到意图标签。
     *
     * @param category 事实类别
     * @return 对应的意图标签；未知类别返回 null
     */
    private String mapCategoryToIntent(String category) {
        return switch (category.toLowerCase()) {
            case "project", "项目" -> "项目进度查询";
            case "preference", "偏好" -> "知识问答";
            case "decision", "决策" -> "状态变更";
            case "relationship", "关系" -> "权限查询";
            case "knowledge", "知识" -> "文档检索";
            default -> null;
        };
    }
}

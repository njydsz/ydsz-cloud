package com.njydsz.agent.domain.profile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户画像实体（映射 ydsz_agt_user_profile 表）
 *
 * <p>持久化存储用户偏好、习惯、关注领域等信息，构成三层长期记忆体系中的"画像层"。
 * 通过对话分析和记忆整合自动更新，为 System Prompt 注入提供个性化上下文。</p>
 *
 * <p><b>三层记忆体系中的定位：</b></p>
 * <ul>
 *   <li>短期记忆 — Redis 对话历史（会话级）</li>
 *   <li>事实记忆 — MemoryExtractedFact（提取的知识点）</li>
 *   <li>用户画像 — UserProfile（本类，长期偏好与习惯模型）</li>
 * </ul>
 *
 * <p><b>Schema 说明</b>：{@code ydsz_agt_user_profile} 表以 {@code user_id} 作为业务主键， 独立 {@code id} 列不存在。
 * 继承的基类 {@code id} 字段在本类中无数据库列映射，仅作为运行时占位； 实际主键通过本类的 {@link #userId} 字段体现。</p>
 *
 * <p><b>线程安全</b>：持久化实体，可变；仅在单请求/单事务内使用，勿跨线程共享。</p>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_user_profile")
public class UserProfile extends MpBaseAuditEntity<String> {

    /** 用户 ID（主键，业务 ID，非自增；覆盖基类 ASSIGN_ID 为 INPUT，对应数据库 user_id 列）。 */
    @TableId(type = IdType.INPUT)
    private String userId;

    /** 偏好语言（zh-CN / en-US） */
    private String preferredLanguage;

    /** 关注领域列表（JSON 列存储） */
    @TableField(value = "interested_domains")
    private String interestedDomainsJson;

    /** 领域查询频次统计（JSON 列，领域 -> 次数映射） */
    @TableField(value = "domain_frequency")
    private String domainFrequencyJson;

    /** 查询风格（如"简洁"、"详细"、"分析型"、"探索型"） */
    private String queryStyle;

    /** 高频意图标签（JSON 列存储） */
    @TableField(value = "common_intents")
    private String commonIntentsJson;

    /** 总交互次数 */
    private Integer totalInteractions;

    /** 最近交互时间 */
    private LocalDateTime lastInteractionAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 获取关注领域列表（从 JSON 列解析）。
     *
     * <p>计算属性，非持久化字段。当 JSON 列为空时返回空列表。</p>
     *
     * @return 关注领域列表（不可变）
     */
    public List<String> getInterestedDomains() {
        if (interestedDomainsJson == null || interestedDomainsJson.isBlank()) {
            return Collections.emptyList();
        }
        List<String> domains = YdszJson.fromJson(interestedDomainsJson, List.class, String.class);
        return domains != null ? Collections.unmodifiableList(domains) : Collections.emptyList();
    }

    /**
     * 设置关注领域列表（序列化到 JSON 列）。
     *
     * @param domains 关注领域列表
     */
    public void setInterestedDomains(List<String> domains) {
        this.interestedDomainsJson = YdszJson.toJson(domains);
    }

    /**
     * 获取领域查询频次统计（从 JSON 列解析）。
     *
     * <p>计算属性，非持久化字段。当 JSON 列为空时返回空 Map。</p>
     *
     * @return 领域 -> 频次 的不可变映射
     */
    public Map<String, Integer> getDomainFrequency() {
        if (domainFrequencyJson == null || domainFrequencyJson.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> freq = YdszJson.fromJsonToMap(domainFrequencyJson, String.class, Integer.class);
        return freq != null ? Collections.unmodifiableMap(freq) : Collections.emptyMap();
    }

    /**
     * 设置领域查询频次统计（序列化到 JSON 列）。
     *
     * @param frequency 领域 -> 频次 映射
     */
    public void setDomainFrequency(Map<String, Integer> frequency) {
        this.domainFrequencyJson = YdszJson.toJson(frequency);
    }

    /**
     * 获取高频意图标签（从 JSON 列解析）。
     *
     * <p>计算属性，非持久化字段。当 JSON 列为空时返回空列表。</p>
     *
     * @return 高频意图标签列表（不可变）
     */
    public List<String> getCommonIntents() {
        if (commonIntentsJson == null || commonIntentsJson.isBlank()) {
            return Collections.emptyList();
        }
        List<String> intents = YdszJson.fromJson(commonIntentsJson, List.class, String.class);
        return intents != null ? Collections.unmodifiableList(intents) : Collections.emptyList();
    }

    /**
     * 设置高频意图标签（序列化到 JSON 列）。
     *
     * @param intents 高频意图标签列表
     */
    public void setCommonIntents(List<String> intents) {
        this.commonIntentsJson = YdszJson.toJson(intents);
    }

    /**
     * 获取 Top N 关注领域（按频次降序排列）。
     *
     * <p>综合 interestedDomains 和 domainFrequency 两个维度：
     * 优先按频次排序，频次相同则保留原顺序。</p>
     *
     * @param n 返回的领域数量上限
     * @return Top N 关注领域列表
     */
    public List<String> getTopDomains(int n) {
        if (n <= 0) {
            n = DEFAULT_TOP_DOMAINS;
        }
        Map<String, Integer> frequency = getDomainFrequency();
        List<String> domains = new ArrayList<>(getInterestedDomains());

        if (frequency.isEmpty()) {
            return domains.subList(0, Math.min(n, domains.size()));
        }

        domains.sort((a, b) -> {
            int freqA = frequency.getOrDefault(a, 0);
            int freqB = frequency.getOrDefault(b, 0);
            return Integer.compare(freqB, freqA);
        });

        return domains.subList(0, Math.min(n, domains.size()));
    }

    /**
     * 增加指定领域的查询频次。
     *
     * @param domain 领域名称
     */
    public void incrementDomainFrequency(String domain) {
        if (domain == null || domain.isBlank()) {
            return;
        }
        Map<String, Integer> frequency = new LinkedHashMap<>(getDomainFrequency());
        frequency.merge(domain, 1, Integer::sum);
        setDomainFrequency(frequency);
    }

    /** 集合初始容量 */
    private static final int COLLECTION_CAPACITY = 16;

    /** 默认 Top 领域数 */
    private static final int DEFAULT_TOP_DOMAINS = 5;
}

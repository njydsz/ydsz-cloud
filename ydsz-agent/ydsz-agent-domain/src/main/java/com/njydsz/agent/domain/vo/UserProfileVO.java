package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 用户画像视图对象（YDIZ-DDD-007：server/web 层禁用 domain Entity）。
 *
 * <p>封装用户偏好、关注领域、查询风格等信息，供 System Prompt 注入与外部查询使用。
 * JSON 列在 infra 层提前解析为强类型集合，避免 server 层依赖实体计算属性。
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Data
public class UserProfileVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认 Top 领域数 */
  private static final int DEFAULT_TOP_DOMAINS = 5;

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 用户 ID（主键） */
  private String userId;

  /** 偏好语言（zh-CN / en-US） */
  private String preferredLanguage;

  /** 领域查询频次统计（领域 -> 次数映射） */
  private Map<String, Integer> domainFrequency;

  /** 关注领域列表（按感兴趣程度排序） */
  private List<String> interestedDomains;

  /** 高频意图标签列表 */
  private List<String> commonIntents;

  /** 查询风格（如"简洁"、"详细"、"分析型"、"探索型"） */
  private String queryStyle;

  /** 总交互次数 */
  private Integer totalInteractions;

  /** 最近交互时间 */
  private LocalDateTime lastInteractionAt;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /**
   * 获取 Top N 关注领域（按频次降序排列）。
   *
   * <p>优先按频次排序，频次相同则保留原 interestedDomains 顺序。
   *
   * @param n 返回的领域数量上限
   * @return Top N 关注领域列表
   */
  public List<String> getTopDomains(int n) {
    if (n <= 0) {
      n = DEFAULT_TOP_DOMAINS;
    }
    if (domainFrequency == null || domainFrequency.isEmpty()) {
      return interestedDomains != null
          ? interestedDomains.subList(0, Math.min(n, interestedDomains.size()))
          : Collections.emptyList();
    }
    List<String> domains = interestedDomains != null
        ? new ArrayList<>(interestedDomains)
        : new ArrayList<>(COLLECTION_CAPACITY);
    domains.sort((a, b) -> {
      int freqA = domainFrequency.getOrDefault(a, 0);
      int freqB = domainFrequency.getOrDefault(b, 0);
      return Integer.compare(freqB, freqA);
    });
    return domains.subList(0, Math.min(n, domains.size()));
  }
}

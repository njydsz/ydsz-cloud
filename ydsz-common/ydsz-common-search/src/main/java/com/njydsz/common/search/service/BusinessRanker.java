package com.njydsz.common.search.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.config.SearchProperties;

/**
 * 业务排序器接口。
 *
 * <p>结合业务因子（热度、新鲜度、个性化）调整 ES 检索结果排序。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class BusinessRanker {

  private final SearchProperties properties;

  /**
   * 对搜索结果进行业务权重重排
   *
   * @param hits 搜索引擎返回的命中列表
   * @param request 原始搜索请求
   * @return 重排后的命中列表
   */
  public List<SearchHit> reRank(List<SearchHit> hits, SearchRequest request) {
    if (hits == null || hits.size() <= 1) {
      return hits;
    }

    String keyword = request.getKeyword() != null ? request.getKeyword().trim().toLowerCase() : "";

    hits.forEach(hit -> hit.setScore(calculateScore(hit, keyword)));

    hits.sort(Comparator.comparing(SearchHit::getScore).reversed());
    return hits;
  }

  private float calculateScore(SearchHit hit, String keyword) {
    float score = hit.getScore() > 0 ? hit.getScore() : 0.0f;

    if (!keyword.isBlank() && hit.getTitle() != null) {
      String titleLower = hit.getTitle().toLowerCase();
      if (titleLower.equals(keyword)) {
        score += 10.0f;
      } else if (titleLower.startsWith(keyword)) {
        score += 5.0f;
      } else if (titleLower.contains(keyword)) {
        score += 3.0f;
      }
    }

    if (hit.getTags() != null && !keyword.isBlank()) {
      for (String tag : hit.getTags()) {
        if (tag != null && tag.toLowerCase().contains(keyword)) {
          score += 2.0f;
          break;
        }
      }
    }

    score += getTimeBoost(hit);
    score += getTypeBoost(hit.getType());

    return score;
  }

  private float getTimeBoost(SearchHit hit) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime createdAt = parseDateTime(hit.getCreatedAt());
    LocalDateTime updatedAt = parseDateTime(hit.getUpdatedAt());

    float boost = 0.0f;
    if (updatedAt != null) {
      long days = ChronoUnit.DAYS.between(updatedAt, now);
      if (days <= 1) {
        boost += 3.0f;
      } else if (days <= 7) {
        boost += 1.5f;
      } else if (days <= 30) {
        boost += 0.5f;
      }
    } else if (createdAt != null) {
      long days = ChronoUnit.DAYS.between(createdAt, now);
      if (days <= 1) {
        boost += 2.0f;
      } else if (days <= 7) {
        boost += 1.0f;
      } else if (days <= 30) {
        boost += 0.3f;
      }
    }
    return boost;
  }

  private float getTypeBoost(String type) {
    if (type == null) {
      return 0.0f;
    }
    return switch (type) {
      case "project" -> 2.0f;
      case "wiki" -> 1.0f;
      case "user" -> 0.5f;
      case "config" -> 0.3f;
      default -> 0.0f;
    };
  }

  private LocalDateTime parseDateTime(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(dateStr);
    } catch (Exception e) {
      try {
        return LocalDateTime.parse(dateStr + "T00:00:00");
      } catch (Exception e2) {
        return null;
      }
    }
  }
}

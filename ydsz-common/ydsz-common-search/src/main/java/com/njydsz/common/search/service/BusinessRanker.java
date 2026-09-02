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

    hits.forEach(hit -> hit.setScore((float) calculateScore(hit, keyword)));

    hits.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
    return hits;
  }

  private double calculateScore(SearchHit hit, String keyword) {
    double score = hit.getScore() > 0 ? hit.getScore() : 0.0;

    if (!keyword.isBlank() && hit.getTitle() != null) {
      String titleLower = hit.getTitle().toLowerCase();
      if (titleLower.equals(keyword)) {
        score += 10.0;
      } else if (titleLower.startsWith(keyword)) {
        score += 5.0;
      } else if (titleLower.contains(keyword)) {
        score += 3.0;
      }
    }

    if (hit.getTags() != null && !keyword.isBlank()) {
      for (String tag : hit.getTags()) {
        if (tag != null && tag.toLowerCase().contains(keyword)) {
          score += 2.0;
          break;
        }
      }
    }

    score += getTimeBoost(hit);
    score += getTypeBoost(hit.getType());

    return score;
  }

  private double getTimeBoost(SearchHit hit) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime createdAt = parseDateTime(hit.getCreatedAt());
    LocalDateTime updatedAt = parseDateTime(hit.getUpdatedAt());

    double boost = 0.0;
    if (updatedAt != null) {
      long days = ChronoUnit.DAYS.between(updatedAt, now);
      if (days <= 1) {
        boost += 3.0;
      } else if (days <= 7) {
        boost += 1.5;
      } else if (days <= 30) {
        boost += 0.5;
      }
    } else if (createdAt != null) {
      long days = ChronoUnit.DAYS.between(createdAt, now);
      if (days <= 1) {
        boost += 2.0;
      } else if (days <= 7) {
        boost += 1.0;
      } else if (days <= 30) {
        boost += 0.3;
      }
    }
    return boost;
  }

  private double getTypeBoost(String type) {
    if (type == null) {
      return 0.0;
    }
    return switch (type) {
      case "project" -> 2.0;
      case "wiki" -> 1.0;
      case "user" -> 0.5;
      case "config" -> 0.3;
      default -> 0.0;
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

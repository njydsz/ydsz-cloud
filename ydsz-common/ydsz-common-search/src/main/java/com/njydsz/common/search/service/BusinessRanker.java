package com.njydsz.common.search.service;

import java.math.BigDecimal;
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

  /** 运算精度（小数位数） */
  private static final int SCALE = 10;

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

  private BigDecimal calculateScore(SearchHit hit, String keyword) {
    BigDecimal score =
        hit.getScore().compareTo(BigDecimal.ZERO) > 0 ? hit.getScore() : BigDecimal.ZERO;

    if (!keyword.isBlank() && hit.getTitle() != null) {
      String titleLower = hit.getTitle().toLowerCase();
      if (titleLower.equals(keyword)) {
        score = score.add(BigDecimal.TEN);
      } else if (titleLower.startsWith(keyword)) {
        score = score.add(BigDecimal.valueOf(5));
      } else if (titleLower.contains(keyword)) {
        score = score.add(BigDecimal.valueOf(3));
      }
    }

    if (hit.getTags() != null && !keyword.isBlank()) {
      for (String tag : hit.getTags()) {
        if (tag != null && tag.toLowerCase().contains(keyword)) {
          score = score.add(BigDecimal.valueOf(2));
          break;
        }
      }
    }

    score = score.add(getTimeBoost(hit));
    score = score.add(getTypeBoost(hit.getType()));

    return score;
  }

  private BigDecimal getTimeBoost(SearchHit hit) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime createdAt = parseDateTime(hit.getCreatedAt());
    LocalDateTime updatedAt = parseDateTime(hit.getUpdatedAt());

    BigDecimal boost = BigDecimal.ZERO;
    if (updatedAt != null) {
      long days = ChronoUnit.DAYS.between(updatedAt, now);
      if (days <= 1) {
        boost = boost.add(BigDecimal.valueOf(3));
      } else if (days <= 7) {
        boost = boost.add(BigDecimal.valueOf(1.5));
      } else if (days <= 30) {
        boost = boost.add(BigDecimal.valueOf(0.5));
      }
    } else if (createdAt != null) {
      long days = ChronoUnit.DAYS.between(createdAt, now);
      if (days <= 1) {
        boost = boost.add(BigDecimal.valueOf(2));
      } else if (days <= 7) {
        boost = boost.add(BigDecimal.ONE);
      } else if (days <= 30) {
        boost = boost.add(BigDecimal.valueOf(0.3));
      }
    }
    return boost;
  }

  private BigDecimal getTypeBoost(String type) {
    if (type == null) {
      return BigDecimal.ZERO;
    }
    return switch (type) {
      case "project" -> BigDecimal.valueOf(2);
      case "wiki" -> BigDecimal.ONE;
      case "user" -> BigDecimal.valueOf(0.5);
      case "config" -> BigDecimal.valueOf(0.3);
      default -> BigDecimal.ZERO;
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

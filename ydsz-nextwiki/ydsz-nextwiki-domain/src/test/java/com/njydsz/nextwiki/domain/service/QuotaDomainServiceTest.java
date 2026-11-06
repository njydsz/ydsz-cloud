package com.njydsz.nextwiki.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link QuotaDomainService} 单元测试。
 *
 * <p>覆盖：默认配额构建、配额校验（超限/边界）、剩余空间计算、百分比阈值判定。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("存储配额领域服务")
class QuotaDomainServiceTest {

  private QuotaDomainService service;

  @BeforeEach
  void setUp() {
    service = new QuotaDomainService();
  }

  private StorageQuotaDTO quota(Long limit, Long used) {
    StorageQuotaDTO dto = new StorageQuotaDTO();
    dto.setQuotaLimit(limit);
    dto.setQuotaUsed(used);
    return dto;
  }

  @Nested
  @DisplayName("buildDefaultQuota 默认配额构建")
  class BuildDefaultQuota {

    @Test
    @DisplayName("5GB 上限 + 10000 文件数上限 + 用量清零")
    void buildsDefaultQuota() {
      StorageQuotaDTO dto = service.buildDefaultQuota("user", "user-1");

      assertThat(dto.getScopeType()).isEqualTo("user");
      assertThat(dto.getScopeId()).isEqualTo("user-1");
      assertThat(dto.getQuotaLimit()).isEqualTo(5L * 1024 * 1024 * 1024);
      assertThat(dto.getQuotaUsed()).isZero();
      assertThat(dto.getFileCountLimit()).isEqualTo(10000);
      assertThat(dto.getFileCountUsed()).isZero();
    }
  }

  @Nested
  @DisplayName("checkQuota 配额校验")
  class CheckQuota {

    @Test
    @DisplayName("未超限通过")
    void withinLimitPasses() {
      service.checkQuota(quota(100L, 50L), 30L);
    }

    @Test
    @DisplayName("刚好等于上限通过")
    void exactlyAtLimitPasses() {
      service.checkQuota(quota(100L, 50L), 50L);
    }

    @Test
    @DisplayName("超出上限抛 QUOTA_INSUFFICIENT")
    void exceedsLimitThrows() {
      BusinessException ex = catchThrowableOfType(() -> service.checkQuota(quota(100L, 50L), 51L), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.QUOTA_INSUFFICIENT.getCode());
    }

    @Test
    @DisplayName("quota 为 null 抛 QUOTA_NOT_FOUND")
    void nullQuotaThrows() {
      BusinessException ex = catchThrowableOfType(() -> service.checkQuota(null, 1L), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.QUOTA_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("quotaLimit 为 null 抛 QUOTA_NOT_FOUND")
    void nullLimitThrows() {
      BusinessException ex = catchThrowableOfType(() -> service.checkQuota(new StorageQuotaDTO(), 1L), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.QUOTA_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("quotaUsed 为 null 按 0 处理")
    void nullUsedTreatedAsZero() {
      service.checkQuota(quota(100L, null), 100L);
    }
  }

  @Nested
  @DisplayName("getRemainingSpace 剩余空间")
  class RemainingSpace {

    @Test
    @DisplayName("正常计算剩余")
    void computesRemaining() {
      assertThat(service.getRemainingSpace(quota(100L, 30L))).isEqualTo(70L);
    }

    @Test
    @DisplayName("已用超上限时返回 0")
    void negativeClampedToZero() {
      assertThat(service.getRemainingSpace(quota(100L, 150L))).isZero();
    }

    @Test
    @DisplayName("quota 为 null 返回 0")
    void nullReturnsZero() {
      assertThat(service.getRemainingSpace(null)).isZero();
    }

    @Test
    @DisplayName("quotaLimit 为 null 返回 0")
    void nullLimitReturnsZero() {
      assertThat(service.getRemainingSpace(new StorageQuotaDTO())).isZero();
    }
  }

  @Nested
  @DisplayName("isOverPercentage 百分比阈值")
  class OverPercentage {

    @ParameterizedTest
    @CsvSource({"100,100,50,true", "100,49,50,false", "100,50,50,true", "100,99,99,true"})
    @DisplayName("使用量 / 上限与阈值比较")
    void comparesRatio(long limit, long used, int percentage, boolean expected) {
      assertThat(service.isOverPercentage(quota(limit, used), percentage)).isEqualTo(expected);
    }

    @Test
    @DisplayName("上限为 0 返回 false（防除零）")
    void zeroLimitReturnsFalse() {
      assertThat(service.isOverPercentage(quota(0L, 0L), 50)).isFalse();
    }

    @Test
    @DisplayName("quota 为 null 返回 false")
    void nullQuotaReturnsFalse() {
      assertThat(service.isOverPercentage(null, 50)).isFalse();
    }

    @Test
    @DisplayName("quotaUsed 为 null 按 0 处理")
    void nullUsedTreatedAsZero() {
      assertThat(service.isOverPercentage(quota(100L, null), 50)).isFalse();
    }
  }
}

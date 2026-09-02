package com.njydsz.common.domain.query;

/**
 * 分页查询风险评估器（纯函数工具）。
 *
 * <p>将深度分页风险评估逻辑从 {@link PageQuery} 上解耦，避免查询参数对象承载
 * 不属于"参数承载"职责的行为。消费方（SafeQueryInnerInterceptor、业务层） 可通过此入口评估分页查询的深度分页风险。
 *
 * @author ydsz-team
 * @see PageQuery
 * @see DeepPaginationRisk
 * @since 26.09.01
 */
public final class PageQueryRiskAssessor {

  private PageQueryRiskAssessor() {
    // 工具类，禁止实例化
  }

  /**
   * 评估查询的深度分页风险（使用默认阈值 10000 / 50000）。
   *
   * @param query 分页查询对象
   * @return 风险等级（SAFE / WARN / REJECT）
   */
  public static DeepPaginationRisk assess(PageQuery query) {
    if (query == null) {
      throw new IllegalArgumentException("PageQuery must not be null");
    }
    return DeepPaginationRisk.assess(query.getOffsetLong());
  }
}

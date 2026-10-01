package com.njydsz.common.domain.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.core.constant.PageConstants;

import static lombok.AccessLevel.PROTECTED;

/**
 * 分页查询参数封装类。
 *
 * <p>承载分页查询的请求参数（页码、页大小、排序项），提供偏移量计算、 排序操作、游标模式判定等基础能力。深度分页风险评估已解耦至 {@link PageQueryRiskAssessor}。
 *
 * @author ydsz-team
 * @see PageQueryRiskAssessor
 * @see DeepPaginationRisk
 * @since 26.10.01
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
public class PageQuery extends BaseQuery {

  private static final long serialVersionUID = 1L;

  /**
   * 排序列名安全校验正则：仅允许字母、数字、下划线，以及 table.column 点号格式。
   *
   * <p>此正则用于防御 SQL 注入：排序列名来自前端参数时，若直接拼接到 ORDER BY 子句可能导致注入攻击。
   * 白名单策略拒绝一切非预期字符（包括空格、引号、分号、注释符等）。
   *
   * <p>示例：{@code user_name} ✅、{@code t.user_name} ✅、{@code user name} ❌、{@code name;DELETE} ❌。
   *
   * @since 26.09.30
   */
  private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

  /**
   * 创建分页查询对象（简化静态工厂，对标 Spring Data {@code PageRequest.of}）。
   *
   * @param pageNum 当前页码（从 1 开始）
   * @param pageSize 每页记录数
   * @return PageQuery 实例
   */
  public static PageQuery of(int pageNum, int pageSize) {
    return PageQuery.builder().pageNum(pageNum).pageSize(pageSize).build();
  }

  /** 搜索关键字最大长度（仅做截断，不做转义） */
  public static final int MAX_SEARCH_KEY_LENGTH = 200;

  /** 当前页码（从1开始）。 */
  @NotNull(message = "pageNum当前页不能为空")
  @Min(value = 1, message = "pageNum最小值为1")
  @Builder.Default
  private Integer pageNum = 1;

  /** 每页显示条数。 */
  @NotNull(message = "pageSize页大小不能为空")
  @Min(value = 1, message = "pageSize最小值为1")
  @Max(value = PageConstants.MAX_PAGE_SIZE, message = "pageSize最大值为" + PageConstants.MAX_PAGE_SIZE)
  @Builder.Default
  private Integer pageSize = PageConstants.DEFAULT_PAGE_SIZE;

  /** 排序项列表（结构化 OrderItem）。 */
  @Builder.Default private List<OrderItem> orderItems = new ArrayList<>(16);

  /**
   * 获取排序项列表，为 Lombok @Builder.Default 提供编码访问入口。
   *
   * @return 排序项列表
   */
  public List<OrderItem> getOrderItems() {
    return orderItems;
  }

  /**
   * 设置排序项列表，为 Lombok @Builder.Default 提供编码赋值入口。
   *
   * @param orderItems 排序项列表
   */
  public void setOrderItems(List<OrderItem> orderItems) {
    this.orderItems = orderItems != null ? orderItems : new ArrayList<>(16);
  }

  /**
   * 追加升序排序项（链式调用）。
   *
   * <p>排序列名必须仅包含字母、数字、下划线，以及可选的 {@code table.column} 点号格式。
   * 任何不符合该模式的列名将触发 {@link IllegalArgumentException}，防止 SQL 注入。
   *
   * @param column 排序列名（必须通过 SQL 安全白名单校验）
   * @return 当前查询对象（支持链式调用）
   * @throws IllegalArgumentException 当列名包含非法字符时
   * @since 26.10.01
   */
  public PageQuery addAscOrder(String column) {
    validateOrderColumn(column);
    if (this.orderItems == null) {
      this.orderItems = new ArrayList<>(16);
    }
    this.orderItems.add(OrderItem.asc(column));
    return this;
  }

  /**
   * 追加降序排序项（链式调用）。
   *
   * <p>排序列名必须仅包含字母、数字、下划线，以及可选的 {@code table.column} 点号格式。
   * 任何不符合该模式的列名将触发 {@link IllegalArgumentException}，防止 SQL 注入。
   *
   * @param column 排序列名（必须通过 SQL 安全白名单校验）
   * @return 当前查询对象（支持链式调用）
   * @throws IllegalArgumentException 当列名包含非法字符时
   * @since 26.10.01
   */
  public PageQuery addDescOrder(String column) {
    validateOrderColumn(column);
    if (this.orderItems == null) {
      this.orderItems = new ArrayList<>(16);
    }
    this.orderItems.add(OrderItem.desc(column));
    return this;
  }

  /**
   * 追加排序项（链式调用）。
   *
   * @param column 排序列名（必须通过 SQL 安全白名单校验）
   * @param isAsc true 升序，false 降序
   * @return 当前查询对象（支持链式调用）
   * @throws IllegalArgumentException 当列名包含非法字符时
   * @since 26.09.30
   */
  public PageQuery addOrder(String column, boolean isAsc) {
    validateOrderColumn(column);
    if (this.orderItems == null) {
      this.orderItems = new ArrayList<>(16);
    }
    this.orderItems.add(OrderItem.of(column, isAsc));
    return this;
  }

  /**
   * 校验排序列名是否安全（SQL 注入防护）。
   *
   * <p>使用白名单正则校验：仅允许字母数字下划线 + 可选的 table.column 格式。
   * 任何非预期字符（空格、引号、分号、注释符、Unicode 控制字符等）均会触发异常。
   *
   * @param column 待校验的列名
   * @throws IllegalArgumentException 当 column 为空白或包含非法字符时
   * @since 26.09.30
   */
  private static void validateOrderColumn(String column) {
    Objects.requireNonNull(column, "排序列名不能为null");
    if (column.isBlank() || !SAFE_COLUMN_PATTERN.matcher(column).matches()) {
      throw new IllegalArgumentException(
          "非法排序列名（疑似 SQL 注入）: \""
              + column
              + "\"。仅允许字母数字下划线及 table.column 格式。");
    }
  }

  /**
   * 获取查询偏移量（从 0 开始），用于 SQL 分页。
   *
   * <p>同时自动触发 {@link PageQueryRiskAssessor} 的深度分页风险评估（WARN 级别时通过 {@link #onDeepPaginationRisk(DeepPaginationRisk)}
   * 钩子通知子类）。REJECT 级别由 {@code SafeQueryInnerInterceptor} 在 SQL 执行前被动拦截。
   *
   * @return 偏移量（从 0 开始）
   * @since 26.10.01
   * @since 26.09.30 增加深度分页风险评估自动触发
   */
  public int getOffset() {
    assessDeepPagination();
    return (pageNum - 1) * pageSize;
  }

  /**
   * 获取查询偏移量（long 类型），用于 SQL 分页。
   *
   * <p>long 类型版本用于超大规模分页（{@code offset > Integer.MAX_VALUE} 时）。
   *
   * @return 偏移量
   * @see #getOffset()
   * @since 26.10.01
   */
  public long getOffsetLong() {
    assessDeepPagination();
    return (long) (pageNum - 1) * pageSize;
  }

  /**
   * 评估深度分页风险并触发钩子。
   *
   * <p>内置调用 {@link PageQueryRiskAssessor#assess(PageQuery)}，当风险等级为 WARN 时自动调用 {@link #onDeepPaginationRisk(DeepPaginationRisk)}。
   * 业务子类可覆写 {@link #onDeepPaginationRisk(DeepPaginationRisk)} 以自定义告警逻辑（如 Sentry 埋点、降级改游标分页）。
   *
   * @since 26.09.30
   */
  private void assessDeepPagination() {
    DeepPaginationRisk risk = PageQueryRiskAssessor.assess(this);
    if (risk == DeepPaginationRisk.WARN) {
      onDeepPaginationRisk(risk);
    }
  }

  /**
   * 深度分页风险预警钩子。
   *
   * <p>当 {@link PageQueryRiskAssessor} 评估当前查询为 {@link DeepPaginationRisk#WARN} 时被自动调用。
   * 默认实现为空操作（no-op），子类可覆写此方法以实现自定义告警逻辑（如 Sentry 埋点、降级策略）。
   *
   * <p>示例（业务模块覆写）：
   *
   * <pre>{@code
   * &#64;Override
   * protected void onDeepPaginationRisk(DeepPaginationRisk risk) {
   *     SentryUtil.tag("deep_pagination", "warn");
   *     log.warn("深度分页预警：{}，建议改用游标分页", this);
   * }
   * }</pre>
   *
   * @param risk 当前评估的风险等级（非 null，始终为 {@link DeepPaginationRisk#WARN}）
   * @since 26.09.30
   */
  @SuppressWarnings("unused")
  protected void onDeepPaginationRisk(DeepPaginationRisk risk) {
    // 默认 no-op，业务子类覆写以接入监控告警
  }

  /**
   * 获取 LIMIT 子句的 limit 值（同 pageSize）。
   *
   * @return 每页条数
   */
  public int getLimit() {
    return pageSize;
  }
}

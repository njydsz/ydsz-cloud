package com.njydsz.common.jdbc.support;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.common.domain.query.OrderItem;
import com.njydsz.common.domain.query.PageQuery;

/**
 * OrderItems 注入器 — 将 {@link PageQuery} 中的结构化排序项（{@link OrderItem}）安全注入 {@link QueryWrapper}。
 *
 * <p>本工具是 YDIZ-DOMAIN-001（OrderItem 排序收敛）核心基础设施。
 * 业务 Repository 在构造完 {@link QueryWrapper} 后，调用 {@link #apply(PageQuery, QueryWrapper)} 统一注入排序条件，
 * 禁止直接调用 {@code QueryWrapper.orderByAsc/orderByDesc(String)}。
 *
 * <p>注入规则：
 *
 * <ul>
 *   <li>{@code null} query 或 {@code null} wrapper：no-op（不注入，避免 NPE）
 *   <li>{@code orderItems} 为空：no-op（不注入，保留 wrapper 原有排序）
 *   <li>{@code orderItems} 非空：逐个转换为 {@code wrapper.orderXxx(column)} 调用
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * QueryWrapper<Entity> wrapper = new QueryWrapper<>();
 * wrapper.eq("tenant_id", query.getTenantId());
 * // 注入 PageQuery 中的排序条件（已做 SQL 注入校验）
 * OrderItemsInjector.apply(query, wrapper);
 * return mapper.selectPage(page, wrapper);
 * }</pre>
 *
 * <p><b>约束范围</b>：所有 ydzs-{module}-infra 层的 Repository/Mapper 实现。
 *
 * @author ydsz-team
 * @see PageQuery#addAscOrder(String)
 * @see PageQuery#addDescOrder(String)
 * @see OrderItem
 * @since 26.09.30
 */
public final class OrderItemsInjector {

  private OrderItemsInjector() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 将 PageQuery 中的排序项注入 QueryWrapper。
   *
   * <p>排序项已在 {@link PageQuery#addAscOrder(String)} / {@link PageQuery#addDescOrder(String)} 中添加时
   * 完成 SQL 注入白名单校验（字母数字下划线+点号），此处仅做方向映射注入。
   *
   * @param query 分页查询对象（可为 {@code null}，此时不注入）
   * @param wrapper MyBatis-Plus 查询包装器（可为 {@code null}，此时不注入）
   * @since 26.09.30
   */
  public static <T> void apply(PageQuery query, QueryWrapper<T> wrapper) {
    if (query == null || wrapper == null) {
      return;
    }
    List<OrderItem> items = query.getOrderItems();
    if (items == null || items.isEmpty()) {
      return;
    }
    for (OrderItem item : items) {
      if (item == null) {
        continue;
      }
      if (item.getDirection() == OrderItem.Direction.DESC) {
        wrapper.orderByDesc(item.getColumn());
      } else {
        wrapper.orderByAsc(item.getColumn());
      }
    }
  }

  /**
   * 将 PageQuery 中的排序项拼接为 ORDER BY SQL 片段。
   *
   * <p>适用于不使用 {@link QueryWrapper} 的原生 SQL 场景（如 {@code String sql = "SELECT * FROM t " + orderItemsSql(query)}）。
   * 返回值不含 "ORDER BY" 前缀，仅包含排序片段（如 {@code "created_at ASC, user_name DESC"}）。
   *
   * @param query 分页查询对象（可为 {@code null}，此时返回空字符串）
   * @return ORDER BY 片段，无排序项时返回空字符串（非 {@code null}）
   * @since 26.09.30
   */
  public static String toOrderBySql(PageQuery query) {
    if (query == null) {
      return "";
    }
    List<OrderItem> items = query.getOrderItems();
    if (items == null || items.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(items.size() * 16);
    for (int i = 0; i < items.size(); i++) {
      OrderItem item = items.get(i);
      if (item == null) {
        continue;
      }
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(item.toSql());
    }
    return sb.toString();
  }
}

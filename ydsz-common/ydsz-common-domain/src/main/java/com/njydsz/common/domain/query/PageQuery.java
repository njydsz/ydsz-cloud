package com.njydsz.common.domain.query.PageQuery;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.json.annotation.JsonIgnore;

import static lombok.AccessLevel.PROTECTED;

/**
 * 分页查询参数封装类。
 *
 * <p>承载分页查询的请求参数（页码、页大小、排序项），提供偏移量计算、 排序操作、游标模式判定等基础能力。深度分页风险评估已解耦至 {@link PageQueryRiskAssessor}。
 *
 * @author ydsz-team
 * @see PageQueryRiskAssessor
 * @see DeepPaginationRisk
 * @since 26.09.01
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
public class PageQuery extends BaseQuery {

  private static final long serialVersionUID = 1L;

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
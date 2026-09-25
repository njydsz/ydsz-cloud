package com.njydsz.common.excel.core.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 列排序解析器 — 统一处理 {@code @ExcelProperty} 注解的列排序逻辑。
 *
 * <p>排序优先级（从高到低）：
 *
 * <ol>
 *   <li>{@link ExcelProperty#index()} 显式列索引（{@code >= 0} 时生效）</li>
 *   <li>{@link ExcelProperty#order()} 排序值（未指定 index 时生效）</li>
 *   <li>Java 字段声明顺序（兜底）</li>
 * </ol>
 *
 * <p>使用方法：传入目标类的 {@link Class}，返回按输出列顺序排列的字段列表。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li>当字段未设置 {@code index} 也未设置 {@code order}（或 order=0）时，按声明顺序排列</li>
 *   <li>{@code index} 显式指定的字段排在最前面，多个按 index 值升序</li>
 *   <li>{@code order} 与 index 互斥——当 index 有效时忽略 order</li>
 *   <li>带 {@code @ExcelIgnore} 注解的字段自动过滤</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ColumnOrderResolver {

  private ColumnOrderResolver() {}

  /**
   * 字段排序元数据。
   */
  public static final class FieldOrder implements Comparable<FieldOrder> {
    private final Field field;
    private final int index;
    private final int order;
    private final int declOrder;

    FieldOrder(Field field, int index, int order, int declOrder) {
      this.field = field;
      this.index = index;
      this.order = order;
      this.declOrder = declOrder;
    }

    public Field getField() {
      return field;
    }

    public int getIndex() {
      return index;
    }

    public int getOrder() {
      return order;
    }

    public int getDeclOrder() {
      return declOrder;
    }

    /**
     * 该字段是否通过 {@code index} 显式指定列位置。
     *
     * @return {@code true} 表示通过 index 指定
     */
    public boolean hasExplicitIndex() {
      return index >= 0;
    }

    /**
     * 排序比较器：index 显式指定的优先 > order 排序 > 声明顺序。
     */
    @Override
    public int compareTo(FieldOrder other) {
      boolean thisIndexed = this.hasExplicitIndex();
      boolean otherIndexed = other.hasExplicitIndex();

      // 两者均有显式 index：按 index 升序
      if (thisIndexed && otherIndexed) {
        return Integer.compare(this.index, other.index);
      }
      // 仅一方有显式 index：排前面
      if (thisIndexed != otherIndexed) {
        return thisIndexed ? -1 : 1;
      }
      // 两者均无显式 index：按 order 升序
      return Integer.compare(this.order, other.order);
    }
  }

  /**
   * 解析类的列排序字段列表。
   *
   * @param clazz 目标类
   * @return 按输出顺序排列的字段列表（已过滤 @ExcelIgnore 和未标注 @ExcelProperty 的字段）
   */
  public static List<Field> resolveOrderedFields(Class<?> clazz) {
    return resolveOrderedFields(clazz, false);
  }

  /**
   * 解析类的列排序字段列表。
   *
   * @param clazz 目标类
   * @param includeIndexZero 是否将 order=0 的字段参与 order 排序（true）或视为"无显式排序"走声明顺序（false）
   * @return 按输出顺序排列的字段列表
   */
  public static List<Field> resolveOrderedFields(Class<?> clazz, boolean includeIndexZero) {
    if (clazz == null) {
      return Collections.emptyList();
    }

    Field[] declaredFields = clazz.getDeclaredFields();
    List<FieldOrder> orderList = new ArrayList<>(declaredFields.length);
    int declOrder = 0;

    for (Field field : declaredFields) {
      // 过滤 @ExcelIgnore
      if (field.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }

      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      if (prop == null) {
        continue;
      }

      int index = prop.index();
      int order = prop.order();

      orderList.add(new FieldOrder(field, index, order, declOrder));
      declOrder++;
    }

    Collections.sort(orderList);

    List<Field> result = new ArrayList<>(orderList.size());
    for (FieldOrder fo : orderList) {
      result.add(fo.getField());
    }
    return result;
  }

  /**
   * 获取列的显示优先级（用于列宽、表头等需要列序信息的场景）。
   *
   * <p>返回值越小表示越靠前。优先级规则：
   * <ul>
   *   <li>有 index：index 值本身（0, 1, 2, ...）</li>
   *   <li>无 index 有 order：Integer.MAX_VALUE / 2 + order（保证在有 index 字段之后）</li>
   *   <li>无 index 无 order：Integer.MAX_VALUE - declOrder（兜底排在最后）</li>
   * </ul>
   *
   * @param prop ExcelProperty 注解
   * @param declOrder 字段声明顺序
   * @return 列优先级数值（越小越靠前）
   */
  public static int getColumnPriority(ExcelProperty prop, int declOrder) {
    if (prop == null) {
      return Integer.MAX_VALUE - declOrder;
    }
    if (prop.index() >= 0) {
      return prop.index();
    }
    if (prop.order() > 0) {
      return Integer.MAX_VALUE / 2 + prop.order();
    }
    return Integer.MAX_VALUE - declOrder;
  }
}

package com.njydsz.common.excel.tabular;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 表格数据行映射器。
 *
 * <p>负责在「原始行数据」（{@code String[] 列值} 或 {@code List<String>}）与「Java 对象」之间双向转换。
 * 流式读取/写入过程中，每行只映射一次，避免反射开销。
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * TabularRowMapper<User> mapper = new DefaultAnnotationRowMapper<>(User.class, headers);
 * User user = mapper.toRow(new String[]{"张三", "25"});
 * }</pre>
 *
 * @param <T> 目标对象类型
 * @author ydsz-team
 * @since 26.09.01
 */
public interface TabularRowMapper<T> {

  /**
   * 获取表头列名列表（顺序与原始数据列一致）。
   *
   * @return 表头列名列表，顺序与 {@code toRow} 入参、{@code fromRow} 返回值的列序一致；
   *     不会为 {@code null}，无表头时返回空列表
   */
  List<String> headers();

  /**
   * 将原始行数据转换为 Java 对象。
   *
   * @param values 原始行数据（与 headers 顺序一致）
   * @return 映射后的对象
   */
  T toRow(String[] values);

  /**
   * 将 Java 对象转换为原始行数据。
   *
   * @param object Java 对象
   * @return 与 headers 顺序一致的字符串数组
   */
  String[] fromRow(T object);

  /**
   * 返回列名 → 列索引 映射（便于按列名快速取值）。
   *
   * @return 列名到列索引的映射
   */
  default Map<String, Integer> headerIndexMap() {
    List<String> hs = headers();
    Map<String, Integer> map = new HashMap<>(hs.size() * 2);
    for (int i = 0; i < hs.size(); i++) {
      map.put(hs.get(i), i);
    }
    return map;
  }

  /**
   * 按列名取单值（默认从 {@link #toRow(String[])} 的结果中取值）。
   *
   * <p>本方法提供默认实现：返回 {@link Optional#empty()}。子类如有需要可覆盖。
   *
   * @param object 行对象
   * @param columnName 列名
   * @return 该列的值；无值时返回 {@link Optional#empty()}
   */
  default Optional<String> getValue(T object, String columnName) {
    return Optional.empty();
  }
}

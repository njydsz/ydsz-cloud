package com.njydsz.common.util.diff;

/**
 * 差异值格式化器接口
 *
 * <p>将字段值转换为可读的字符串格式，用于差异报告展示。 实现类需提供无参构造函数，由 {@link DiffCalculator} 通过反射实例化。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * public class StatusFormatter implements DiffValueFormatter {
 *     @Override
 *     public String format(Object value) {
 *         if (value == null) return "未设置";
 *         return switch ((Integer) value) {
 *             case 0 -> "禁用";
 *             case 1 -> "启用";
 *             default -> "未知";
 *         };
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DiffValueFormatter {

  /**
   * 格式化字段值
   *
   * @param value 字段值（可能为 null）
   * @return 格式化后的字符串
   */
  String format(Object value);
}

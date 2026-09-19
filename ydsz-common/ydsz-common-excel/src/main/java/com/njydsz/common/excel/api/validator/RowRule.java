package com.njydsz.common.excel.api.validator;

/**
 * 行级自定义校验规则 — 在 JSR-303 注解标准校验之外扩展跨字段、跨行等自定义校验逻辑。
 *
 * <p>实现本接口后通过 {@code ExcelReader.addRule()} 注册到读取器，
 * 每行数据对象构建完成后（字段映射 + 注解校验通过）回调本规则。
 *
 * <p>典型用途：
 * <ul>
 *   <li>跨字段校验（如"开始日期必须早于结束日期"）</li>
 *   <li>跨行校验（基于前一行数据状态判定当前行合法性）</li>
 *   <li>业务唯一性校验（如"同一批次中订单号不能重复"）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ExcelFacade.read("orders.xlsx", OrderDTO.class)
 *   .addRule((order, rowNum) -> {
 *     if (order.getStartDate().after(order.getEndDate())) {
 *       throw new IllegalArgumentException(
 *           "第" + rowNum + "行：开始日期不能晚于结束日期");
 *     }
 *   })
 *   .doRead(order -> service.save(order));
 * }</pre>
 *
 * @param <T> 行映射的数据类型
 * @author ydsz-team
 * @since 26.09.19
 */
@FunctionalInterface
public interface RowRule<T> {

  /**
   * 校验单行数据。
   *
   * @param rowData 行映射后的数据对象（非 {@code null}）
   * @param rowNumber Excel 行号（从 1 开始，含表头行）
   * @throws IllegalArgumentException 校验失败时抛出，消息描述将作为错误提示返回给调用方
   */
  void validate(T rowData, int rowNumber);
}

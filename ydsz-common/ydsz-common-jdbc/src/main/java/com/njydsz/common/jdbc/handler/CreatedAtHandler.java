package com.njydsz.common.jdbc.handler;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;

import com.njydsz.common.jdbc.config.FieldFillConfiguration;
import com.njydsz.common.jdbc.enums.FieldFillStrategyEnum;

/**
 * 创建时间字段填充处理器
 *
 * <p>在 INSERT 操作时自动为记录设置创建时间（created_at 字段）。 使用当前系统时间作为填充值，格式为 "yyyy-MM-dd HH:mm:ss"。
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li>新建用户记录时自动记录创建时间
 *   <li>业务数据录入时自动记录入库时间
 *   <li>审计日志创建时记录操作时间
 * </ul>
 *
 * <h2>SQL 转换示例</h2>
 *
 * <pre>
 * // 原始 SQL
 * INSERT INTO sys_user (name, email) VALUES ('张三', 'zhangsan@example.com');
 *
 * // 转换后 SQL
 * INSERT INTO sys_user (name, email, created_at) VALUES ('张三', 'zhangsan@example.com', '2026-04-09 10:30:00');
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UpdatedAtHandler 更新时间处理器
 * @see FieldFillConfiguration 字段填充配置
 */
public class CreatedAtHandler extends AbstractFieldFillHandler {

  /**
   * 构造创建时间填充处理器
   *
   * @param fieldFillConfig 字段填充配置
   */
  public CreatedAtHandler(FieldFillConfiguration fieldFillConfig) {
    super(FieldFillStrategyEnum.INSERT, fieldFillConfig.getCreateAtIntercept());
  }

  @Override
  protected Expression doGetFieldFillValue() {
    // 使用 CURRENT_TIMESTAMP 函数，由数据库计算时间戳，避免 SQL 文本嵌入
    Function func = new Function();
    func.setName("CURRENT_TIMESTAMP");
    return func;
  }

  @Override
  protected String getDefaultColumn() {
    return "created_at";
  }
}

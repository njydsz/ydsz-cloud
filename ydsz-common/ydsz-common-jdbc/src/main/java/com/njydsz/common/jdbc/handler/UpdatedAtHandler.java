package com.njydsz.common.jdbc.handler;

import com.njydsz.common.jdbc.config.FieldFillConfiguration;
import com.njydsz.common.jdbc.enums.FieldFillStrategyEnum;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;

/**
 * 更新时间字段填充处理器
 *
 * <p>在 INSERT 和 UPDATE 操作时自动为记录设置更新时间（updated_at 字段）。 使用数据库 CURRENT_TIMESTAMP 函数作为填充值，确保时间戳来源与
 * created_at 一致。
 *
 * <h2>时间戳来源</h2>
 *
 * <p>与 {@link CreatedAtHandler} 统一使用数据库 {@code CURRENT_TIMESTAMP} 函数，
 * 由数据库服务器计算时间戳。这样在多实例部署场景下，所有时间戳均来自数据库时钟， 避免应用服务器时钟偏差导致 created_at 与 updated_at 不一致。
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li>记录最后修改时间，用于数据审计
 *   <li>业务数据更新时刷新时间戳
 *   <li>缓存失效时间计算依据
 * </ul>
 *
 * <h2>SQL 转换示例</h2>
 *
 * <pre>
 * // INSERT 原始 SQL
 * INSERT INTO sys_user (name, email) VALUES ('张三', 'zhangsan@example.com');
 * // INSERT 转换后 SQL
 * INSERT INTO sys_user (name, email, updated_at) VALUES ('张三', 'zhangsan@example.com', CURRENT_TIMESTAMP);
 *
 * // UPDATE 原始 SQL
 * UPDATE sys_user SET email = 'new@example.com' WHERE id = 1;
 * // UPDATE 转换后 SQL
 * UPDATE sys_user SET email = 'new@example.com', updated_at = CURRENT_TIMESTAMP WHERE id = 1;
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CreatedAtHandler 创建时间处理器
 * @see FieldFillConfiguration 字段填充配置
 */
public class UpdatedAtHandler extends AbstractFieldFillHandler {

  /**
   * 构造更新时间填充处理器
   *
   * @param fieldFillConfig 字段填充配置
   */
  public UpdatedAtHandler(FieldFillConfiguration fieldFillConfig) {
    super(FieldFillStrategyEnum.INSERT_UPDATE, fieldFillConfig.getUpdateAtIntercept());
  }

  @Override
  protected Expression doGetFieldFillValue() {
    // 使用 CURRENT_TIMESTAMP 函数，由数据库计算时间戳，与 CreatedAtHandler 保持一致
    Function func = new Function();
    func.setName("CURRENT_TIMESTAMP");
    return func;
  }

  @Override
  protected String getDefaultColumn() {
    return "updated_at";
  }
}

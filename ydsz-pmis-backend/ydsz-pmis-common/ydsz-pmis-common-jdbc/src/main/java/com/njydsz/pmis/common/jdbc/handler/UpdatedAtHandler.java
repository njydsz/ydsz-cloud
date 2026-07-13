package com.njydsz.pmis.common.jdbc.handler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.njydsz.pmis.common.jdbc.config.FieldFillConfiguration;
import com.njydsz.pmis.common.jdbc.enums.FieldFillStrategyEnum;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.TimestampValue;

/**
 * 更新时间字段填充处理器
 *
 * <p>在 INSERT 和 UPDATE 操作时自动为记录设置更新时间（updated_at 字段）。
 * 使用当前系统时间作为填充值，格式为 "yyyy-MM-dd HH:mm:ss"。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>记录最后修改时间，用于数据审计</li>
 *   <li>业务数据更新时刷新时间戳</li>
 *   <li>缓存失效时间计算依据</li>
 * </ul>
 *
 * <h2>SQL 转换示例</h2>
 * <pre>
 * // INSERT 原始 SQL
 * INSERT INTO sys_user (name, email) VALUES ('张三', 'zhangsan@example.com');
 * // INSERT 转换后 SQL
 * INSERT INTO sys_user (name, email, updated_at) VALUES ('张三', 'zhangsan@example.com', '2026-04-09 10:30:00');
 *
 * // UPDATE 原始 SQL
 * UPDATE sys_user SET email = 'new@example.com' WHERE id = 1;
 * // UPDATE 转换后 SQL
 * UPDATE sys_user SET email = 'new@example.com', updated_at = '2026-04-09 11:00:00' WHERE id = 1;
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see CreatedAtHandler 创建时间处理器
 * @see FieldFillConfiguration 字段填充配置
 * @since 1.0.0
 */
public class UpdatedAtHandler extends AbstractFieldFillHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        String date = LocalDateTime.now().format(FORMATTER);
        return new TimestampValue(date);
    }

    @Override
    protected String getDefaultColumn() {
        return "updated_at";
    }
}
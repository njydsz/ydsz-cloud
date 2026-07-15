package com.njydsz.pmis.common.jdbc.handler;

import com.njydsz.pmis.common.jdbc.domain.InterceptConfig;
import com.njydsz.pmis.common.jdbc.enums.FieldFillStrategyEnum;

import net.sf.jsqlparser.expression.Expression;

/**
 * 字段填充处理器抽象基类
 *
 * <p>提供字段填充处理的基础实现，继承自 {@link AbstractSqlHandler}。
 * 通过组合 {@link FieldFillStrategyEnum} 策略控制字段在 INSERT/UPDATE 操作时的填充行为。</p>
 *
 * <h2>填充策略</h2>
 * <ul>
 *   <li>{@link FieldFillStrategyEnum#INSERT}：仅在 INSERT 时填充</li>
 *   <li>{@link FieldFillStrategyEnum#UPDATE}：仅在 UPDATE 时填充</li>
 *   <li>{@link FieldFillStrategyEnum#INSERT_UPDATE}：在 INSERT 和 UPDATE 时都填充</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * public class MyFieldFillHandler extends AbstractFieldFillHandler {
 *     public MyFieldFillHandler() {
 *         super(FieldFillStrategyEnum.INSERT, interceptConfig);
 *     }
 *
 *     {@literal @}Override
 *     protected Expression doGetFieldFillValue() {
 *         return new StringValue("current_user");
 *     }
 *
 *     {@literal @}Override
 *     protected String getDefaultColumn() {
 *         return "created_by";
 *     }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see FieldFillHandler
 * @see FieldFillStrategyEnum
 */
public abstract class AbstractFieldFillHandler extends AbstractSqlHandler implements FieldFillHandler {

    /**
     * 字段填充策略
     */
    private FieldFillStrategyEnum fieldFillStrategyEnum;

    /**
     * 构造字段填充处理器
     *
     * @param fieldFillStrategyEnum 填充策略
     * @param interceptConfig       拦截配置
     */
    public AbstractFieldFillHandler(FieldFillStrategyEnum fieldFillStrategyEnum, InterceptConfig interceptConfig) {
        super(interceptConfig);
        this.fieldFillStrategyEnum = fieldFillStrategyEnum;
    }

    /**
     * 获取填充值表达式
     *
     * <p>子类实现此方法提供具体的填充值。</p>
     *
     * @return 填充值表达式
     */
    protected abstract Expression doGetFieldFillValue();

    @Override
    public Expression getFieldFillValue() {
        return doGetFieldFillValue();
    }

    @Override
    public String getFieldFillColumn() {
        return handleColumn(interceptConfig.getColumn());
    }

    @Override
    public boolean createIgnore(String tableName) {
        if (FieldFillStrategyEnum.UPDATE == fieldFillStrategyEnum) {
            return true;
        }
        return defaultIgnoreStrategy(tableName, interceptConfig);
    }

    @Override
    protected boolean customIgnore() {
        return false;
    }

    @Override
    public boolean updateIgnore(String tableName) {
        if (FieldFillStrategyEnum.INSERT == fieldFillStrategyEnum) {
            return true;
        }
        return defaultIgnoreStrategy(tableName, interceptConfig);
    }
}
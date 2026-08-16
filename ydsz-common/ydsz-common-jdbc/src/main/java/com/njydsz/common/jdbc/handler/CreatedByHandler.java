package com.njydsz.common.jdbc.handler;

import java.util.Optional;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.model.CurrentUser;
import com.njydsz.common.jdbc.config.FieldFillConfiguration;
import com.njydsz.common.jdbc.constant.AuditFieldConstants;
import com.njydsz.common.jdbc.enums.FieldFillStrategyEnum;

/**
 * 创建人字段填充处理器
 *
 * <p>在 INSERT 操作时自动为记录设置创建人标识（created_by 字段）。
 * 从当前请求上下文中获取用户信息，如果获取失败则使用英文标识符 {@value com.njydsz.common.jdbc.constant.AuditFieldConstants#CREATED_BY_SYSTEM}，
 * 由上层 MessageSource 通过 key {@code audit.created_by.system} 解析为展示文本。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>新建业务记录时自动记录创建人</li>
 *   <li>数据审计追踪时记录数据创建者</li>
 *   <li>业务流程记录创建者信息</li>
 * </ul>
 *
 * <h2>SQL 转换示例</h2>
 * <pre>
 * // 原始 SQL
 * INSERT INTO sys_user (name, email) VALUES ('张三', 'zhangsan@example.com');
 *
 * // 转换后 SQL
 * INSERT INTO sys_user (name, email, created_by) VALUES ('张三', 'zhangsan@example.com', 'user-001');
 * </pre>
 *
 * <h2>依赖说明</h2>
 * <p>该处理器依赖 {@link AuthInfoUtils} 获取当前用户上下文，
 * 需要确保请求链路中已正确设置用户认证信息。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UpdatedByHandler 更新人处理器
 * @see FieldFillConfiguration 字段填充配置
 * @see RequestContext 请求上下文
 */
public class CreatedByHandler extends AbstractFieldFillHandler {

    /**
     * 构造创建人填充处理器
     *
     * @param fieldFillConfig 字段填充配置
     */
    public CreatedByHandler(FieldFillConfiguration fieldFillConfig) {
        super(FieldFillStrategyEnum.INSERT, fieldFillConfig.getCreatedByIntercept());
    }

    @Override
    protected Expression doGetFieldFillValue() {
        Object authObj = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);
        CurrentUser authInfo = authObj instanceof CurrentUser auth ? auth : null;
        // 非 Web 上下文（定时任务、MQ 消费）返回英文标识符 "system"，
        // 由上层 MessageSource 通过 key "audit.created_by.system" 解析为展示文本
        String uniqueId = Optional.ofNullable(authInfo)
                .map(CurrentUser::getUniqueId).orElse(AuditFieldConstants.CREATED_BY_SYSTEM);
        return new StringValue(uniqueId);
    }

    @Override
    protected String getDefaultColumn() {
        return "created_by";
    }
}

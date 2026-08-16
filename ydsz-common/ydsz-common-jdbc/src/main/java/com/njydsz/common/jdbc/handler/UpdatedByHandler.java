package com.njydsz.common.jdbc.handler;

import java.util.Optional;

import com.njydsz.common.jdbc.config.FieldFillConfiguration;
import com.njydsz.common.jdbc.constant.AuditFieldConstants;
import com.njydsz.common.jdbc.enums.FieldFillStrategyEnum;
import com.njydsz.common.core.model.AuthInfo;
import com.njydsz.common.auth.context.AuthInfoUtils;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

/**
 * 更新人字段填充处理器
 *
 * <p>在 INSERT 和 UPDATE 操作时自动为记录设置更新人标识（updated_by 字段）。
 * 从当前请求上下文中获取用户信息，如果获取失败则使用英文标识符 {@value com.njydsz.common.jdbc.constant.AuditFieldConstants#UPDATED_BY_SYSTEM}，
 * 由上层 MessageSource 通过 key {@code audit.updated_by.system} 解析为展示文本。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>记录数据最后修改人，用于审计追踪</li>
 *   <li>业务数据更新时记录操作者</li>
 *   <li>数据变更历史记录</li>
 * </ul>
 *
 * <h2>SQL 转换示例</h2>
 * <pre>
 * // INSERT 原始 SQL
 * INSERT INTO sys_user (name, email) VALUES ('张三', 'zhangsan@example.com');
 * // INSERT 转换后 SQL
 * INSERT INTO sys_user (name, email, updated_by) VALUES ('张三', 'zhangsan@example.com', 'user-001');
 *
 * // UPDATE 原始 SQL
 * UPDATE sys_user SET email = 'new@example.com' WHERE id = 1;
 * // UPDATE 转换后 SQL
 * UPDATE sys_user SET email = 'new@example.com', updated_by = 'user-002' WHERE id = 1;
 * </pre>
 *
 * <h2>依赖说明</h2>
 * <p>该处理器依赖 {@link AuthInfoUtils} 获取当前用户上下文，
 * 需要确保请求链路中已正确设置用户认证信息。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CreatedByHandler 创建人处理器
 * @see FieldFillConfiguration 字段填充配置
 * @see RequestContext 请求上下文
 */
public class UpdatedByHandler extends AbstractFieldFillHandler {

    /**
     * 构造更新人填充处理器
     *
     * @param fieldFillConfig 字段填充配置
     */
    public UpdatedByHandler(FieldFillConfiguration fieldFillConfig) {
        super(FieldFillStrategyEnum.INSERT_UPDATE, fieldFillConfig.getUpdateByIntercept());
    }

    @Override
    protected Expression doGetFieldFillValue() {
        Object authObj = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);\n        AuthInfo authInfo = authObj instanceof AuthInfo auth ? auth : null;
        // 非 Web 上下文（定时任务、MQ 消费）返回英文标识符 "system"，
        // 由上层 MessageSource 通过 key "audit.updated_by.system" 解析为展示文本
        String uniqueId = Optional.ofNullable(authInfo)
                .map(AuthInfo::getUniqueId).orElse(AuditFieldConstants.UPDATED_BY_SYSTEM);
        return new StringValue(uniqueId);
    }

    @Override
    protected String getDefaultColumn() {
        return "updated_by";
    }
}

package com.njydsz.common.auth.model;

import com.njydsz.common.auth.annotation.AuthColPermission;

/**
 * 列权限信息可注入标记接口。
 *
 * <p>当方法参数实现此接口并标注了 {@link AuthColPermission} 时，
 * 切面会在方法调用前将 {@link ColumnScopeInfo} 注入到该参数中。
 *
 * <p><b>实现示例：</b>
 * <pre>
 * public class UserQuery implements ColumnScopeAware {
 *     private ColumnScopeInfo columnScope;
 *
 *     &#64;Override
 *     public void setColumnScope(ColumnScopeInfo columnScopeInfo) {
 *         this.columnScope = columnScopeInfo;
 *     }
 *
 *     public ColumnScopeInfo getColumnScope() {
 *         return columnScope;
 *     }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see AuthColPermission
 * @see ColumnScopeInfo
 */
public interface ColumnScopeAware {

    /**
     * 注入列权限作用域信息。
     *
     * @param columnScopeInfo 列权限作用域
     */
    void setColumnScope(ColumnScopeInfo columnScopeInfo);
}

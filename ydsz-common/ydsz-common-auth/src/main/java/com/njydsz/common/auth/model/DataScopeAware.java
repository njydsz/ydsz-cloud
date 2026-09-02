package com.njydsz.common.auth.model;

/**
 * 数据权限可注入标记接口。
 *
 * <p>当方法或类标注 @AuthRowPermission 时，如果参数实现该接口，则切面会在方法调用前注入 DataScopeInfo。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DataScopeAware {
  /**
   * 注入数据权限信息。
   *
   * @param dataScope 数据权限信息
   */
  void setDataScope(DataScopeInfo dataScope);
}

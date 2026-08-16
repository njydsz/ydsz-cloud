package com.njydsz.common.web.auth;

import com.njydsz.common.base.auth.BaseAuthInfo;

/**
 * Web 端认证上下文信息
 *
 * <p>继承 {@link BaseAuthInfo}，为 Web 端（管理端）提供服务类型标识。 解析逻辑由基类 {@link BaseAuthInfo} 统一处理。
 *
 * @author ydsz-team
 * @see BaseAuthInfo
 * @since 1.0.0
 */
public class WebAuthInfo extends BaseAuthInfo {

  /**
   * 获取服务类型编码
   *
   * @return Web 端服务类型编码 {@code "webService"}
   */
  @Override
  public String getServiceTypeCode() {
    return "webService";
  }
}

package com.njydsz.gateway.config;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.gateway.constant.InternalSignatureHeaderConstants;

/**
 * 网关层内部常量定义
 *
 * <p>网关与下游服务之间约定的内部请求头常量。 网关负责注入这些头，下游服务通过 {@code BaseAuthFilter} 解析。
 *
 * <h3>使用约束</h3>
 *
 * <ul>
 *   <li>所有 X-User-* / X-Internal-* 头在 {@link com.njydsz.gateway.filter.AuthGlobalFilter}
 *       中统一注入，{@link com.njydsz.gateway.config.PathGuard#internalHeaders()} 中定义需剥离的客户端伪造头集合
 *   <li>新增内部头时必须同步更新 PathGuard 列表 + 下游 BaseAuthFilter 解析逻辑
 *   <li>下游服务信任网关的前提是 {@link com.njydsz.gateway.config.InternalHeaderSigner} 签名校验通过
 * </ul>
 *
 * <p><b>常量治理（v1.2.0）：</b>所有原来硬编码的请求头字符串已改为委托 {@link HeaderConstants} 中的权威定义，
 * 消除跨模块重复声明，确保请求头键名全局唯一可控。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class GatewayConstants {

  private GatewayConstants() {
    throw new UnsupportedOperationException("Utility class - do not instantiate");
  }

  /** 链路追踪 ID 请求头（委托 {@link HeaderConstants#TRACE_ID_HEADER}） */
  public static final String HEADER_TRACE_ID = HeaderConstants.TRACE_ID_HEADER;

  /** 用户 ID 请求头（委托 {@link AuthHeaderConstants#X_USER_ID}） */
  public static final String HEADER_USER_ID = AuthHeaderConstants.X_USER_ID;

  /** 用户名请求头（委托 {@link AuthHeaderConstants#X_USERNAME}） */
  public static final String HEADER_USERNAME = AuthHeaderConstants.X_USERNAME;

  /** 用户角色请求头（CSV）（委托 {@link AuthHeaderConstants#X_USER_ROLES}） */
  public static final String HEADER_USER_ROLES = AuthHeaderConstants.X_USER_ROLES;

  /** 用户权限请求头（CSV）（委托 {@link AuthHeaderConstants#X_USER_PERMISSIONS}） */
  public static final String HEADER_USER_PERMISSIONS = AuthHeaderConstants.X_USER_PERMISSIONS;

  /** 内部头签名请求头（委托 {@link InternalSignatureHeaderConstants#X_INTERNAL_SIG}） */
  public static final String HEADER_INTERNAL_SIG = InternalSignatureHeaderConstants.X_INTERNAL_SIG;

  /** 内部头签名时间戳请求头（委托 {@link InternalSignatureHeaderConstants#X_INTERNAL_TS}） */
  public static final String HEADER_INTERNAL_TS = InternalSignatureHeaderConstants.X_INTERNAL_TS;

  /**
   * 内部头签名 nonce 请求头（P0-6 防重放）（委托 {@link HeaderConstants#X_INTERNAL_NONCE}）。
   *
   * <p>网关为每个请求生成唯一 nonce，纳入 HMAC 签名 payload 后透传给下游。 下游服务使用 {@code
   * NonceCache.verifyAndConsume(nonce)} 校验是否重复， 配合时间戳窗口形成"一次性签名"机制。
   */
  public static final String HEADER_INTERNAL_NONCE =
      InternalSignatureHeaderConstants.X_INTERNAL_NONCE;

  /** 租户 ID 请求头（委托 {@link DataPermissionHeaderConstants#X_TENANT_ID}） */
  public static final String HEADER_TENANT_ID = DataPermissionHeaderConstants.X_TENANT_ID;

  /** 请求唯一标识请求头（委托 {@link HeaderConstants#X_REQUEST_ID}） */
  public static final String HEADER_REQUEST_ID = HeaderConstants.X_REQUEST_ID;
}

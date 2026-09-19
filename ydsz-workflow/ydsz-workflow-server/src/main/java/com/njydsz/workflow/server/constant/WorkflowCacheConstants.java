package com.njydsz.workflow.server.constant;

/**
 * 流程引擎（ydsz-workflow）专属缓存常量 — 缓存名称与 Key 模板定义。
 *
 * <p>对标原 {@code com.njydsz.common.cache.constant.CacheConstants} 中 {@code FLOW_*} 区段， 下沉到本模块以避免
 * ydsz-common-cache 与各业务模块的交叉维护。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class WorkflowCacheConstants {

  private WorkflowCacheConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ============================== 工作流模块缓存名 ==============================

  /**
   * 流程定义已发布版本缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:flow:def:published:{flowCode}:{version}}。
   * 当流程定义发布/下线/删除时通过 {@code @CacheEvict(allEntries=true)} 失效。
   */
  public static final String FLOW_DEF_PUBLISHED_CACHE = "flow_def_published";

  /**
   * 流程定义最新版本缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:flow:def:latest:{flowCode}}。 当流程定义发布新版本时失效。
   */
  public static final String FLOW_DEF_LATEST_CACHE = "flow_def_latest";

  /**
   * 三方审批账号按用户 ID 查询缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:flow:thirdparty:by_user:{userId}:{platform}}。
   * 当账号绑定/解绑时失效。
   */
  public static final String FLOW_THIRDPARTY_BY_USER_CACHE = "flow_thirdparty_by_user";

  /**
   * 三方审批账号按 OpenID 查询缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:flow:thirdparty:by_openid:{openId}:{platform}}。
   * 当账号绑定/解绑时失效。
   */
  public static final String FLOW_THIRDPARTY_BY_OPENID_CACHE = "flow_thirdparty_by_openid";
}

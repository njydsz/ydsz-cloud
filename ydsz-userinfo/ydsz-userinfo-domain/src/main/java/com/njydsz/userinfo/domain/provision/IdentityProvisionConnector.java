package com.njydsz.userinfo.domain.provision;

/**
 * 身份供给连接器接口（P0-1 Identity Provisioning 管道）。
 *
 * <p>定义从外部身份源（LDAP、JDBC、SCIM API 等）抽取用户/组织架构数据的统一契约。
 * 每种外部源通过实现本接口接入中心化的供给调度框架。
 *
 * <p><b>职责边界：</b>
 *
 * <ul>
 *   <li>负责从外部源读取原始用户/部门数据，返回标准化的 {@link ProvisionRecord}</li>
 *   <li>不直接写入本地数据库 — 写入由上层 {@code ProvisionOrchestrator} 负责</li>
 *   <li>支持全量拉取（{@link #pullAll()}）和增量拉取（{@link #pullIncremental(String)}）</li>
 * </ul>
 *
 * <p><b>实现约定：</b>
 *
 * <ul>
 *   <li>实现类应为 Spring Bean，通过 {@link #getConnectorType()} 作为唯一标识注册</li>
 *   <li>实现类必须为无状态，可多线程并发调用</li>
 *   <li>所有异常封装为 {@link ProvisionException}，由上层统一处理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface IdentityProvisionConnector {

  /**
   * 获取连接器类型标识。
   *
   * <p>全局唯一，用作注册表键和配置键（如 {@code LDAP}、 {@code JDBC}、 {@code SCIM}）。
   *
   * @return 连接器类型字符串（大写）
   */
  String getConnectorType();

  /**
   * 全量拉取外部身份源的用户记录。
   *
   * <p>用于首次同步或周期性全量校验场景。实现应返回外部源中所有当前有效的用户记录。
   *
   * @return 外部源用户记录列表；无数据时返回空列表
   * @throws ProvisionException 连接失败或数据读取异常
   */
  ProvisionRecordPage pullAll();

  /**
   * 增量拉取外部身份源的用户记录。
   *
   * <p>仅返回自上次同步标记以来变更的用户记录。实现方可通过本地时间戳或
   * 外部源的 {@code whenChanged} / {@code modifyTimestamp} 等属性实现增量过滤。
   *
   * @param lastSyncToken 上次同步返回的令牌（由 {@link ProvisionRecordPage#getNextSyncToken()} 获取）；
   *                      传 null 表示首次增量拉取（等同于全量）
   * @return 增量用户记录分页
   * @throws ProvisionException 连接失败或数据读取异常
   */
  ProvisionRecordPage pullIncremental(String lastSyncToken);

  /**
   * 检查连接器是否可用。
   *
   * <p>用于健康检查和调度器判断是否执行同步。默认返回 true。
   *
   * @return true 表示连接器配置完整、外部源可达
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * 获取连接器显示名称（用于管理界面和日志展示）。
   *
   * @return 人类可读的连接器名称
   */
  String getDisplayName();
}

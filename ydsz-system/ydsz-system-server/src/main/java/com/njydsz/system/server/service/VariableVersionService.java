package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.vo.VariableVersionVO;

/**
 * 变量版本 Service 接口
 *
 * <p>提供变量（{@code variableKey}）变更版本记录和查询能力，支持回滚审计。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本记录</b>：{@link #createVersion} — 变量增删改时自动调用，记录变更前的快照
 *   <li><b>历史查询</b>：{@link #listByResourceKey} — 管理后台「变量版本管理」数据源
 *   <li><b>回滚支持</b>：{@link #rollbackTo} — 一键回滚到任意历史版本
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.VariableService 变量 Service（写操作时调用 {@link #createVersion}）
 * @see com.njydsz.system.domain.entity.VariableVersion 变量版本实体
 */
public interface VariableVersionService {

  /**
   * 按变量键查询版本历史
   *
   * <p>返回该变量键下所有版本记录，按 {@code effectiveDate} 倒序。
   *
   * @param resourceKey 变量键
   * @return 版本列表（按生效时间倒序）
   */
  List<VariableVersionVO> listByResourceKey(String resourceKey);

  /**
   * 创建版本快照
   *
   * <p>由 {@link VariableService} 在写操作成功后调用。{@code snapshotJson} 一般为变更前的变量 JSON 字符串。
   *
   * @param resourceKey 变量键
   * @param version 版本号
   * @param changeLog 变更说明
   * @param snapshotJson 变量 JSON 快照（可为 {@code null}）
   * @return 新建版本记录主键 ID
   */
  String createVersion(String resourceKey, String version, String changeLog, String snapshotJson);

  /**
   * 回滚变量到指定版本
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在且未删除
   *   <li>查询当前变量作为回滚前快照（用于审计）
   *   <li>从 {@code snapshotJson} 反序列化并更新变量
   *   <li>创建新版本记录（标记回滚来源，保持完整审计链）
   *   <li>失效该变量键对应缓存
   * </ol>
   *
   * <p><b>审计设计：</b>回滚操作创建一个<b>新版本</b>而非覆盖历史，保持不可变记录原则。
   *
   * @param resourceKey 变量键
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID（审计用途）
   * @return 新创建的回滚版本 ID
   * @throws com.njydsz.common.exception.custom.BusinessException 版本不存在时抛出
   *     VARIABLE_VERSION_NOT_FOUND
   */
  String rollbackTo(String resourceKey, String targetVersion, String operatorId);
}

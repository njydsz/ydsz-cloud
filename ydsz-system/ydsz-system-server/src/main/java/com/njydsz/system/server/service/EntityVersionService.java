package com.njydsz.system.server.service;
import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.server.service.rollback.RollbackStrategy;



/**
 * 统一实体版本 Service 接口
 *
 * <p>为 Config/Dict/Variable 提供统一的变更版本记录和查询能力，替代原有的三套独立版本服务
 * （ConfigVersionService/DictVersionService/VariableVersionService）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本记录</b>：{@link #createVersion} — 资源增删改时自动调用，记录变更前的快照
 *   <li><b>历史查询</b>：{@link #listByResourceTypeAndKey} — 管理后台「版本管理」数据源
 *   <li><b>回滚支持</b>：{@link #rollbackTo} — 一键回滚到任意历史版本
 * </ul>
 *
 * <p><b>版本生成策略：</b>版本号由调用方传入，推荐使用 {@link com.njydsz.system.server.util.SystemVersionUtils#nextVersion()}
 * 生成可读版本号（格式 {@code vyyyyMMdd-HHmmss-SSS}），替代原始时间戳版本号。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface EntityVersionService {

  /** 资源类型：系统配置 */
  String RESOURCE_TYPE_CONFIG = "CONFIG";

  /** 资源类型：字典 */
  String RESOURCE_TYPE_DICT = "DICT";

  /** 资源类型：系统变量 */
  String RESOURCE_TYPE_VARIABLE = "VARIABLE";

  /**
   * 按资源类型 + 资源键查询版本历史
   *
   * <p>返回该资源下所有版本记录，按 {@code effectiveDate} 倒序。
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @return 版本列表（按生效时间倒序）
   */
  List<EntityVersionVO> listByResourceTypeAndKey(String resourceType, String resourceKey);

  /**
   * 按资源类型 + 资源键分页查询版本历史（P2-3 分页优化）。
   *
   * <p>支持翻页查询，适用于版本量大的场景（如高频变更的配置）。
   *
   * @param query 分页查询条件（resourceType / resourceKey / pageNum / pageSize）
   * @return 分页结果（含总记录数）
   */
  PageResponse<List<EntityVersionVO>> pageByResourceTypeAndKey(EntityVersionPageQuery query);

  /**
   * 创建版本快照
   *
   * <p>由业务 Service 在写操作成功后调用。入参聚合为 {@link
   * com.njydsz.system.domain.dto.EntityVersionDTO}（参数 ≤ 5 个，符合《云顶编码规范》）。
   *
   * @param dto 版本创建参数（含资源类型/键/分组/版本号/变更说明/快照）
   * @return 新建版本记录主键 ID
   */
  String createVersion(com.njydsz.system.domain.dto.EntityVersionDTO dto);

  /**
   * 回滚资源到指定版本
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在且未删除
   *   <li>执行回滚策略（由调用方提供，实现资源重建逻辑）
   *   <li>创建新版本记录（标记回滚来源，保持完整审计链）
   * </ol>
   *
   * <p><b>审计设计：</b>回滚操作创建一个<b>新版本</b>而非覆盖历史，保持不可变记录原则。
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID（审计用途）
   * @param rollbackStrategy 回滚策略（反序列化快照并重建资源）
   * @return 新创建的回滚版本 ID
   * @throws com.njydsz.common.exception.custom.BusinessException 版本不存在时抛出
   */
  String rollbackTo(
      String resourceType,
      String resourceKey,
      String targetVersion,
      String operatorId,
      RollbackStrategy rollbackStrategy);
}

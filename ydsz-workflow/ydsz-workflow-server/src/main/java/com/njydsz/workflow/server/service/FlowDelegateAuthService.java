package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.workflow.domain.dto.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.infra.entity.FlowDelegateAuth;

/**
 * 流程委托授权服务。
 *
 * <p>A 委托 B 代为审批。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowDelegateAuthService {

  /**
   * 将授权 Post DTO 转换为 DO 实体
   *
   * <p>符合 DDD 分层规范：DTO→DO 转换逻辑封装在 Service 层。
   *
   * @param dto 授权 Post DTO
   * @return 授权 DO 实体
   * @since 1.0.0
   */
  FlowDelegateAuth postDtoToEntity(FlowDelegateAuthPostDTO dto);

  /**
   * 创建授权
   *
   * <p>创建前会校验：
   *
   * <ol>
   *   <li>被授权人 ≠ 授权人
   *   <li>生效时间合理（endTime > startTime）
   *   <li>无时间区间冲突的同 scope 授权
   * </ol>
   *
   * @param auth 授权信息
   * @return 授权 ID
   */
  String create(FlowDelegateAuth auth);

  /**
   * 撤回授权
   *
   * @param authId 授权 ID
   * @param ownerUserId 授权人 ID（用于权限校验）
   */
  void revoke(String authId, String ownerUserId);

  /**
   * 启用/停用
   *
   * @param authId 参数说明
   * @param status 参数说明
   * @param operatorId 参数说明
   */
  void updateStatus(String authId, String status, String operatorId);

  /**
   * 查"我设置的"授权列表（返回 DO，供 Service 层内部使用）
   *
   * @param ownerUserId 参数说明
   * @param tenantId 参数说明
   * @param status 参数说明
   * @return 返回值说明
   */
  List<FlowDelegateAuth> listMine(String ownerUserId, String tenantId, String status);

  /**
   * 查"代理给我的"授权列表（返回 DO，供 Service 层内部使用）
   *
   * @param delegateUserId 参数说明
   * @param tenantId 参数说明
   * @param status 参数说明
   * @return 返回值说明
   */
  List<FlowDelegateAuth> listAsDelegate(String delegateUserId, String tenantId, String status);

  /**
   * 查"我设置的"授权列表（返回 VO，符合 DDD 分层规范）
   *
   * @param ownerUserId 授权人 ID
   * @param tenantId 租户 ID
   * @param status 状态筛选（可选）
   * @return 授权 VO 列表
   * @since 1.0.0
   */
  List<FlowDelegateAuthVO> listMineVO(String ownerUserId, String tenantId, String status);

  /**
   * 查"代理给我的"授权列表（返回 VO，符合 DDD 分层规范）
   *
   * @param delegateUserId 代理人 ID
   * @param tenantId 租户 ID
   * @param status 状态筛选（可选）
   * @return 授权 VO 列表
   * @since 1.0.0
   */
  List<FlowDelegateAuthVO> listAsDelegateVO(String delegateUserId, String tenantId, String status);

  /**
   * 匹配代理规则 — 创建任务前调用
   *
   * <p>任务创建时如果 ownerUserId 命中代理规则，assigneeId 改写为 delegateUserId， 并将原 ownerUserId 写入 assignorId 字段。
   *
   * @param tenantId 租户
   * @param ownerUserId 当前解析出的办理人 ID
   * @param flowCode 流程编码
   * @param nodeCode 节点编码
   * @return 命中的代理规则（无则返回 null）
   */
  FlowDelegateAuth matchAuth(String tenantId, String ownerUserId, String flowCode, String nodeCode);

  /**
   * 扫描并标记过期授权（每 5 分钟一次）
   *
   * @return 本次过期条数
   */
  int scanAndMarkExpired();

  /**
   * 分页查询"我代理处理的日志"
   *
   * @param delegateUserId 参数说明
   * @param page 参数说明
   * @param size 参数说明
   * @return 返回值说明
   */
  YdszResponse<?> listDelegateLog(String delegateUserId, int page, int size);

  /**
   * 分页查询"我的被代理日志"
   *
   * @param ownerUserId 参数说明
   * @param page 参数说明
   * @param size 参数说明
   * @return 返回值说明
   */
  YdszResponse<?> listOwnerLog(String ownerUserId, int page, int size);

  /**
   * P1-7: 链式解析代理人
   *
   * <p>代理链能力。当 A 委派给 B，B 又委派给 C 时， A 的任务最终应流转到 C。本方法递归匹配代理人，直到：
   *
   * <ul>
   *   <li>被代理人无进一步委派 → 返回最终代理人
   *   <li>达到最大链深度（5） → 返回当前代理人（防止循环）
   *   <li>检测到循环（A→B→A） → 返回当前代理人并记录警告
   * </ul>
   *
   * @param tenantId 租户 ID
   * @param ownerUserId 原始办理人 ID
   * @param flowCode 流程编码
   * @param nodeCode 节点编码
   * @return 最终代理人 ID（无委派时返回 ownerUserId 本身）
   */
  String resolveDelegateChain(
      String tenantId, String ownerUserId, String flowCode, String nodeCode);
}

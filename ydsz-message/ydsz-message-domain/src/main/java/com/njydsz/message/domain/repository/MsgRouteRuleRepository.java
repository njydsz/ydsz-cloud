package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgRouteRuleQuery;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;

/**
 * 消息路由规则仓储接口（domain 层契约）。
 *
 * <p>定义消息路由规则的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgRouteRuleVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgRouteRuleQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgRouteRuleVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgRouteRuleRepository {

  /**
   * 保存路由规则（插入或更新）。
   *
   * @param vo 路由规则 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgRouteRuleVO vo);

  /**
   * 根据主键查询路由规则。
   *
   * @param id 规则 ID
   * @return 路由规则 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgRouteRuleVO> findById(String id);

  /**
   * 更新路由规则。
   *
   * @param vo 路由规则 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgRouteRuleVO vo);

  /**
   * 根据主键删除路由规则。
   *
   * @param id 规则 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 按条件查询路由规则列表。
   *
   * @param query 查询参数
   * @return 路由规则 VO 列表
   */
  List<MsgRouteRuleVO> findList(MsgRouteRuleQuery query);

  /**
   * 分页查询路由规则。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgRouteRuleVO>> findPage(MsgRouteRuleQuery query);
}

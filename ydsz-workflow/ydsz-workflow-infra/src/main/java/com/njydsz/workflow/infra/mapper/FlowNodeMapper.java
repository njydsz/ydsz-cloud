package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowNode;

/**
 * 流程节点 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_node</code>，维护流程定义中每个节点（开始/审批/分支/结束）的元数据。
 *
 * <p>节点是流程执行的最小单元（审批人/CC人/超时/驳回策略），引擎按节点推进实例。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_node_code — (definitionId+nodeCode) 唯一索引
 *   <li>idx_node_type — 节点类型过滤索引（START/APPROVAL/BRANCH/END）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowNode 流程节点实体
 * @see com.njydsz.workflow.server.service.FlowNodeService 流程节点 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNode> {

  /**
   * 根据定义 ID 查全部节点
   *
   * @param definitionId 流程定义 ID
   * @return 节点列表
   */
  List<FlowNode> selectByDefinitionId(@Param("definitionId") String definitionId);

  /**
   * 根据 definitionId + nodeCode 查单节点
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return 匹配的节点实体；不存在返回 null
   */
  FlowNode selectByCode(
      @Param("definitionId") String definitionId, @Param("nodeCode") String nodeCode);

  /**
   * 查开始节点
   *
   * @param definitionId 流程定义 ID
   * @return 开始节点实体
   */
  FlowNode selectStartNode(@Param("definitionId") String definitionId);

  /**
   * 查结束节点列表
   *
   * @param definitionId 流程定义 ID
   * @return 结束节点列表
   */
  List<FlowNode> selectEndNodes(@Param("definitionId") String definitionId);

  /**
   * 删除某定义的全部节点（重定义时用）
   *
   * @param definitionId 流程定义 ID
   * @return 受影响行数
   */
  int deleteByDefinitionId(@Param("definitionId") String definitionId);
}

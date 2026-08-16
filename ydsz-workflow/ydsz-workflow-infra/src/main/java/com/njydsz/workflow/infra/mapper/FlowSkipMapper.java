package com.njydsz.workflow.infra.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.njydsz.workflow.domain.entity.FlowSkip;

/**
 * 节点跳转 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_skip</code>，记录节点之间的跳转关系（正向流转/退回）。</p>
 * <p>跳转规则由 BPMN 2.0 的 SequenceFlow 解析得到（含条件表达式），是引擎查找前驱/后继节点的核心数据。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_skip_id — 跳转 ID 唯一索引</li>
 *   <li>idx_from_node — 源节点维度查询索引</li>
 *   <li>idx_to_node — 目标节点维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.domain.entity.FlowSkip 节点跳转实体
 * @see com.njydsz.workflow.server.engine.FlowEngine 流程引擎
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowSkipMapper extends BaseMapper<FlowSkip> {

    /**
     * 查某定义的全部跳转
     */
    List<FlowSkip> selectByDefinitionId(@Param("definitionId") String definitionId);

    /**
     * 查某节点的出发跳转
     */
    List<FlowSkip> selectByNodeCode(@Param("definitionId") String definitionId,
                                      @Param("nodeCode") String nodeCode,
                                      @Param("skipType") String skipType);

    /**
     * 查指向某节点的跳转（用于退回时找前驱）
     */
    List<FlowSkip> selectByNextNode(@Param("definitionId") String definitionId,
                                      @Param("nextNodeCode") String nextNodeCode);

    /**
     * 删除某定义的全部跳转
     */
    int deleteByDefinitionId(@Param("definitionId") String definitionId);
}

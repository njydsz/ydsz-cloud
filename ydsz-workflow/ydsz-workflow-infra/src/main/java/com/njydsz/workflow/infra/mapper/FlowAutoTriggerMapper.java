package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowAutoTrigger;

/**
 * 流程自动触发规则 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_auto_trigger</code>，存储流程间的自动触发规则。
 *
 * <p>自动触发用于「源流程完成后自动发起目标流程」场景（如项目立项完成后自动发起「项目启动会议」流程），按源流程编码 + 触发条件定义。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_rule_id — 规则 ID 唯一索引
 *   <li>idx_source_flow — 源流程编码过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.domain.entity.FlowAutoTrigger 自动触发规则实体
 * @see com.njydsz.workflow.server.service.FlowAutoTriggerService 自动触发 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowAutoTriggerMapper extends BaseMapper<FlowAutoTrigger> {

  /**
   * 按源流程编码查询所有启用的触发规则
   *
   * @param sourceFlowCode 源流程编码
   * @return 启用的触发规则列表
   */
  List<FlowAutoTrigger> selectEnabledBySourceFlowCode(
      @Param("sourceFlowCode") String sourceFlowCode);
}

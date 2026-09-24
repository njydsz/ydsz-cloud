package com.njydsz.agent.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.agent.domain.entity.AgentTraceStep;

/**
 * Agent 执行链路步骤 Mapper（domain 去 MP 注解版）
 *
 * <p>映射 {@code ydsz_agt_trace_step} 表，该表使用 (traceId, stepIndex) 复合业务键，
 * 不使用 BaseMapper（避免主键映射冲突）。基于 MyBatis 注解直接操作 PO。
 *
 * <p>使用 {@link AgentTraceStepPO}（基础设施层 PO）承载 MyBatis-Plus @TableName 注解。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Mapper
public interface AgentTraceStepMapper {

  /**
   * 批量插入步骤明细。
   *
   * @param steps 步骤 PO 列表
   * @return 影响行数
   */
  @Insert("<script>"
      + "INSERT INTO ydsz_agt_trace_step (trace_id, step_index, step_type, content, "
      + "input_json, output_json, duration_ms, cost) VALUES "
      + "<foreach collection='list' item='s' separator=','>"
      + "(#{s.traceId}, #{s.stepIndex}, #{s.stepType}, #{s.content}, "
      + "#{s.inputJson}, #{s.outputJson}, #{s.durationMs}, #{s.cost})"
      + "</foreach>"
      + "</script>")
  int batchInsert(@Param("list") List<AgentTraceStep> steps);

  /**
   * 查询指定链路的所有步骤。
   *
   * @param traceId 链路 ID
   * @return 步骤 PO 列表（按 stepIndex 升序）
   */
  @Select("SELECT trace_id, step_index, step_type, content, input_json, output_json, duration_ms, cost "
      + "FROM ydsz_agt_trace_step WHERE trace_id = #{traceId} ORDER BY step_index ASC")
  List<AgentTraceStep> selectByTraceId(@Param("traceId") String traceId);

  /**
   * 查询指定链路指定类型的步骤。
   *
   * @param traceId 链路 ID
   * @param stepType 步骤类型
   * @return 步骤 PO 列表
   */
  @Select("SELECT trace_id, step_index, step_type, content, input_json, output_json, duration_ms, cost "
      + "FROM ydsz_agt_trace_step WHERE trace_id = #{traceId} AND step_type = #{stepType} "
      + "ORDER BY step_index ASC")
  List<AgentTraceStep> selectByTraceIdAndType(@Param("traceId") String traceId,
      @Param("stepType") String stepType);
}

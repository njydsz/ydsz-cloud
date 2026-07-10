package com.njydsz.pmis.agent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.agent.AgentTraceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent Tracing 数据访问层（P2-3 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTraceDO> {

    /**
     * 按 trace_id 查询完整链路（按 step_index / created_at 升序）。
     *
     * @param traceId 链路 ID
     * @return span 列表（按时间顺序）
     */
    List<AgentTraceDO> selectByTraceId(@Param("traceId") String traceId);

    /**
     * 按业务维度查询最近 N 条 trace span。
     *
     * @param bizType 业务类型
     * @param bizId   业务 ID
     * @param limit   返回条数
     * @return span 列表
     */
    List<AgentTraceDO> selectByBiz(@Param("bizType") String bizType,
                                    @Param("bizId") String bizId,
                                    @Param("limit") int limit);
}

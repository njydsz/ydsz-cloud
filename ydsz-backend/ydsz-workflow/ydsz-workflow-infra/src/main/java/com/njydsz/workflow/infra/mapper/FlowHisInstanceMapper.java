package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowHisInstance;

/**
 * P2-3 流程实例归档 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowHisInstanceMapper extends BaseMapper<FlowHisInstance> {

    /**
     * 批量插入归档实例
     *
     * @param instances 待归档实例列表
     * @return 实际插入行数
     */
    int batchInsert(@Param("list") List<FlowHisInstance> instances);

    /**
     * 按主表 ID 列表删除已归档的实例
     *
     * @param ids 主表 ID 列表
     * @return 实际删除行数
     */
    int deleteByOriginalIds(@Param("ids") List<Long> ids);

    /**
     * 按租户聚合归档统计
     */
    List<Map<String, Object>> aggregateByTenant(@Param("tenantId") String tenantId);

    /**
     * 查询指定时间范围前的归档记录
     */
    List<FlowHisInstance> selectByArchivedAtBefore(@Param("threshold") LocalDateTime threshold,
                                                     @Param("limit") int limit);
}

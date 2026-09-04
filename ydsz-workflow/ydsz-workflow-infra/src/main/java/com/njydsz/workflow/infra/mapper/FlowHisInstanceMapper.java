package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowHisInstance;

/**
 * P2-3 流程实例归档 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_his_instance</code>，存储结束态（APPROVED/REJECTED/TERMINATED）的流程实例归档数据。
 *
 * <p>归档表与运行表分离，避免 {@code ydsz_flow_instance} 无限膨胀；归档数据用于历史查询/审计/统计分析。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_instance_id — 实例 ID 唯一索引（1:1 关联运行实例）
 *   <li>idx_end_at — 结束时间排序索引（按时间段查询）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowHisInstance 流程实例归档实体
 * @see com.njydsz.workflow.server.service.FlowArchiveService 归档 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
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
   * 按租户聚合归档统计。
   *
   * @param tenantId 租户 ID
   * @return 归档统计结果列表
   */
  List<Map<String, Object>> aggregateByTenant(@Param("tenantId") String tenantId);

  /**
   * 查询指定时间范围前的归档记录
   *
   * @param threshold 归档时间阈值（早于此时间的记录）
   * @param limit 返回条数上限
   * @return 历史实例列表
   */
  List<FlowHisInstance> selectByArchivedAtBefore(
      @Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}

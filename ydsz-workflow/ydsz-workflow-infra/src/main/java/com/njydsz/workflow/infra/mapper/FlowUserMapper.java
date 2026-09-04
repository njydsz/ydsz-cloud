package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowUser;

/**
 * 流程用户 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_user</code>，记录会签/或签场景下每个任务的处理人与处理状态。
 *
 * <p>会签模式下多个 FlowUser 关联同一任务；或签模式下任何一个人处理完即视为任务完成。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_task — (taskId+userId) 唯一索引
 *   <li>idx_user_status — 用户+处理状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.entity.FlowUser 流程用户实体
 * @see com.njydsz.workflow.server.service.FlowTaskService 待办 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowUserMapper extends BaseMapper<FlowUser> {

  /**
   * 查某 task 的所有用户
   *
   * @param taskId 任务 ID
   * @return 该任务下的全部用户关联列表
   */
  List<FlowUser> selectByTaskId(@Param("taskId") String taskId);

  /**
   * 标记用户已处理
   *
   * @param taskId 任务 ID
   * @param userId 处理人用户 ID
   * @param comment 处理意见
   * @param processAt 处理时间
   * @return 受影响行数
   */
  int markProcessed(
      @Param("taskId") String taskId,
      @Param("userId") String userId,
      @Param("comment") String comment,
      @Param("processAt") LocalDateTime processAt);

  /**
   * 查某实例某节点未处理的用户（会签场景）
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 节点编码
   * @return 未处理的用户关联列表
   */
  List<FlowUser> selectUnprocessedByInstanceAndNode(
      @Param("instanceId") String instanceId, @Param("nodeCode") String nodeCode);

  /**
   * 查某用户待办关联的任务 ID（通过 ydsz_flow_user 表）
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 关联的任务 ID 列表
   */
  List<Long> selectTaskIdsByUser(
      @Param("userId") String userId, @Param("tenantId") String tenantId);

  /**
   * 批量插入
   *
   * @param list 待插入的用户实体列表
   * @return 实际插入行数
   */
  int batchInsert(@Param("list") List<FlowUser> list);
}

package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowDefinition;

/**
 * 流程定义 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_definition</code>，存储流程定义主表。
 *
 * <p>流程定义是「流程模板的某个具体版本」（含 BPMN 2.0 XML / JSON DSL / 节点配置），按 version 管理，支持发布/灰度/版本回滚。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_flow_code_version — (flowCode+version+tenantId) 唯一索引
 *   <li>idx_is_publish — 发布状态过滤索引
 *   <li>idx_activity_status — 激活状态过滤索引（0 挂起 / 1 激活）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowDefinition 流程定义实体
 * @see com.njydsz.workflow.server.service.FlowDefinitionService 流程定义 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowDefinitionMapper extends BaseMapper<FlowDefinition> {

  /**
   * 根据 flowCode + version 查最新已发布版本
   *
   * @param flowCode 流程定义编码
   * @param version 版本号
   * @param tenantId 租户 ID
   * @return 已发布的流程定义；不存在返回 null
   */
  FlowDefinition selectPublished(
      @Param("flowCode") String flowCode,
      @Param("version") String version,
      @Param("tenantId") String tenantId);

  /**
   * 根据 flowCode 查最新版本（不区分发布状态）
   *
   * @param flowCode 流程定义编码
   * @param tenantId 租户 ID
   * @return 最新版本流程定义；不存在返回 null
   */
  FlowDefinition selectLatestByCode(
      @Param("flowCode") String flowCode, @Param("tenantId") String tenantId);

  /**
   * 发布（更新 is_publish）
   *
   * @param id 流程定义 ID
   * @param isPublish 发布状态（1 发布/9 取消发布）
   * @return 受影响行数
   */
  int publish(@Param("id") String id, @Param("isPublish") Integer isPublish);

  /**
   * P2-27: 失效同 flowCode 的其他已发布版本（is_publish 置 9）
   *
   * @param flowCode 流程编码
   * @param exceptId 排除的 definitionId（目标版本）
   * @param tenantId 租户 ID
   * @return 受影响行数
   */
  int deactivateByFlowCode(
      @Param("flowCode") String flowCode,
      @Param("exceptId") String exceptId,
      @Param("tenantId") String tenantId);

  /**
   * P2-28: 更新流程定义激活状态（0 挂起 / 1 激活）
   *
   * @param id 流程定义 ID
   * @param activityStatus 激活状态
   * @return 受影响行数
   */
  int updateActivityStatus(@Param("id") String id, @Param("activityStatus") Integer activityStatus);

  /**
   * P3-1: 查询同 flowCode + tenant 下处于灰度中（CANARYING）的所有定义，按 version 倒序
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 灰度中定义列表（按 version desc）
   */
  List<FlowDefinition> selectCanaryingByCode(
      @Param("flowCode") String flowCode, @Param("tenantId") String tenantId);

  /**
   * P3-1: 查询同 flowCode + tenant 下的所有定义（含历史版本），按 version 倒序
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 所有定义列表
   */
  List<FlowDefinition> selectByFlowCode(
      @Param("flowCode") String flowCode, @Param("tenantId") String tenantId);

  /**
   * P2-4: CAS 加锁 — 仅当当前 lockedBy 为空或已超时才更新成功。
   *
   * <p>使用乐观锁 version 校验 + 条件更新，确保并发安全。
   *
   * @param id 流程定义 ID
   * @param lockedBy 持锁人 ID
   * @param lockedAt 加锁时间
   * @param expectedOldBy 期望的旧持锁人（NULL=未锁定场景），用于续约校验
   * @param timeoutExpired 超时阈值（早于此时间的锁视为已过期，可被抢占）
   * @param version 乐观锁版本号
   * @return 受影响行数（1=成功，0=失败）
   */
  int casLock(
      @Param("id") String id,
      @Param("lockedBy") String lockedBy,
      @Param("lockedAt") LocalDateTime lockedAt,
      @Param("expectedOldBy") String expectedOldBy,
      @Param("timeoutExpired") LocalDateTime timeoutExpired,
      @Param("version") Integer version);

  /**
   * P2-4: CAS 解锁 — 仅当 lockedBy 为持锁人时才清空。
   *
   * @param id 流程定义 ID
   * @param expectedBy 期望的持锁人 ID
   * @param version 乐观锁版本号
   * @return 受影响行数（1=成功，0=失败）
   */
  int casUnlock(
      @Param("id") String id,
      @Param("expectedBy") String expectedBy,
      @Param("version") Integer version);
}

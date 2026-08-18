package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;

/**
 * 流程实例批量操作器
 *
 * <p>负责流程实例的<b>批量操作</b>，包含批量启动和批量终止能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>批量启动</b>：{@link #batchStartInstances} — 一次性提交多个流程实例，每个实例独立事务，单个失败不影响其他
 *   <li><b>批量终止</b>：{@link #batchTerminate} — 批量终止实例列表，含子流程级联终止
 * </ul>
 *
 * <p><b>事务策略：</b>本类方法<b>不开启 {@code @Transactional}</b>，而是通过委托 {@link FlowInstanceLifecycleManager} 执行单条操作（每条独立事务），
 * 避免长事务锁等待。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowInstanceBatchOperator {

  /** 流程实例生命周期管理器，单条操作的实际执行者（每条操作独立事务） */
  private final FlowInstanceLifecycleManager lifecycleManager;

  /** 流程实例 Mapper，负责 ydsz_flow_instance 表的增删改查 */
  private final FlowInstanceMapper instanceMapper;

  /**
   * P2-6: 批量发起流程实例。
   *
   * <p>每个 {@link FlowStartProcessDTO} 通过 {@link FlowInstanceLifecycleManager#start} 独立事务发起， 单个失败不影响其他实例。返回成功发起的
   * instanceId 列表 + 失败项明细。
   *
   * @param dtos 流程启动参数列表（不能为空，最多 100 条）
   * @return Map 包含：
   *     <ul>
   *       <li>{@code successCount} (int) — 成功发起数
   *       <li>{@code failedCount} (int) — 失败数
   *       <li>{@code instanceIds} (List&lt;String&gt;) — 成功发起的实例 ID 列表
   *       <li>{@code failedItems} (List&lt;Map&gt;) — 失败项明细，每项含 index / businessId / reason
   *     </ul>
   * @throws SysException 当 dtos 为空或超过 100 条时
   */
  public Map<String, Object> batchStartInstances(List<FlowStartProcessDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_e4f5a6b7")
          .build();
    }
    if (dtos.size() > 100) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_f5a6b7c8")
          .params(dtos.size(), 100)
          .build();
    }

    int successCount = 0;
    List<String> instanceIds = new ArrayList<>();
    List<Map<String, Object>> failedItems = new ArrayList<>();

    for (int i = 0; i < dtos.size(); i++) {
      FlowStartProcessDTO dto = dtos.get(i);
      String businessId = dto != null ? dto.getBusinessId() : null;
      try {
        // 委托生命周期管理器执行（独立事务）
        String instanceId = lifecycleManager.start(dto);
        successCount++;
        instanceIds.add(instanceId);
        log.info("[Flow] 批量发起第 {} 条成功: businessId={} instanceId={}", i + 1, businessId, instanceId);
      } catch (Exception e) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("index", i + 1);
        fail.put("businessId", businessId);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.put("reason", reason);
        failedItems.add(fail);
        log.warn("[Flow] 批量发起第 {} 条失败: businessId={} reason={}", i + 1, businessId, reason);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failedCount", failedItems.size());
    result.put("instanceIds", instanceIds);
    result.put("failedItems", failedItems);
    log.info(
        "[Flow] 批量发起完成: total={} success={} failed={}",
        dtos.size(),
        successCount,
        failedItems.size());
    return result;
  }

  /**
   * P1-8: 批量终止流程实例（含子流程级联终止）
   *
   * <p>终止指定实例列表，同时级联终止所有关联的子流程实例。 每个 terminate 在独立事务中执行，单个失败不影响其它。
   *
   * @param instanceIds 实例 ID 列表
   * @param reason 终止原因
   * @return 实际终止的实例数（含级联子流程）
   */
  public int batchTerminate(List<String> instanceIds, String reason) {
    if (instanceIds == null || instanceIds.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (String instanceId : instanceIds) {
      try {
        // 委托生命周期管理器执行（独立事务）
        lifecycleManager.terminate(instanceId, reason);
        count++;
        // 级联终止子流程实例
        List<FlowInstanceDO> children =
            instanceMapper.selectList(
                new LambdaQueryWrapper<FlowInstanceDO>()
                    .eq(FlowInstanceDO::getParentInstanceId, instanceId)
                    .eq(FlowInstanceDO::getFlowStatus, FlowInstanceStatus.RUNNING.name()));
        for (FlowInstanceDO child : children) {
          try {
            lifecycleManager.terminate(child.getId(), "级联终止: " + reason);
            count++;
          } catch (Exception e) {
            log.warn(
                "[Flow] 级联终止子流程失败: parentId={} childId={} err={}",
                instanceId,
                child.getId(),
                e.getMessage());
          }
        }
      } catch (Exception e) {
        log.warn("[Flow] 批量终止实例失败: instanceId={} err={}", instanceId, e.getMessage());
      }
    }
    log.info("[Flow] 批量终止完成: requested={} actual={}", instanceIds.size(), count);
    return count;
  }
}

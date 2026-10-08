package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.vo.FlowBatchStartResultVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;

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
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowInstanceBatchOperator {

  /** 流程实例生命周期管理器，单条操作的实际执行者（每条操作独立事务） */
  private final FlowInstanceLifecycleManager lifecycleManager;

  /** 流程实例仓储，负责 ydsz_flow_instance 的领域持久化 */
  private final FlowInstanceRepository instanceRepository;

  /** 虚拟线程执行器：用于批量启动实例的并发执行 */
  private static final ExecutorService VIRTUAL_EXECUTOR =
      ExecutorUtils.newVirtualThreadExecutor("flow-batch-start-");

  /**
   * P2-6: 批量发起流程实例 — 虚拟线程并发执行。
   *
   * <p>每个 {@link FlowStartProcessDTO} 通过 {@link FlowInstanceLifecycleManager#start} 独立事务发起，
   * 单个失败不影响其他实例。返回成功发起的 instanceId 列表 + 失败项明细。
   *
   * <p>使用虚拟线程池并发处理全部启动请求，IO 等待（DB 写入 / 推进）不阻塞载体线程，
   * 从而显著减少批量发起的总耗时（受限于 HikariCP 连接池和数据库吞吐）。
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
  public FlowBatchStartResultVO batchStartInstances(List<FlowStartProcessDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.batch.empty")
          .build();
    }
    if (dtos.size() > 100) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.batch.size.exceeded")
          .params(dtos.size(), 100)
          .build();
    }

    // 将每条启动请求提交到虚拟线程池，全部提交后再统一 join 收集结果
    List<CompletableFuture<StartResultItem>> futures = IntStream.range(0, dtos.size())
        .mapToObj(i -> CompletableFuture.supplyAsync(
            () -> executeSingleStart(dtos.get(i), i + 1),
            VIRTUAL_EXECUTOR))
        .toList();

    // 汇总结果
    int successCount = 0;
    List<String> instanceIds = new ArrayList<>(dtos.size());
    List<FlowBatchStartResultVO.FailedItemVO> failedItems = new ArrayList<>(dtos.size());
    for (CompletableFuture<StartResultItem> future : futures) {
      StartResultItem item = future.join();
      if (item.success()) {
        successCount++;
        instanceIds.add(item.instanceId());
      } else {
        failedItems.add(item.fail());
      }
    }

    FlowBatchStartResultVO result = new FlowBatchStartResultVO();
    result.setSuccessCount(successCount);
    result.setFailedCount(failedItems.size());
    result.setInstanceIds(instanceIds);
    result.setFailedItems(failedItems);
    log.info(
        "[Flow] 批量发起完成: total={} success={} failed={}",
        dtos.size(),
        successCount,
        failedItems.size());
    return result;
  }

  /**
   * 单条启动结果实体（record），封装成功/失败两类信息。
   */
  private record StartResultItem(
      int index,
      boolean success,
      String instanceId,
      FlowBatchStartResultVO.FailedItemVO fail) {}

  /**
   * 执行单条流程实例启动，返回成功实例 ID 或构建失败明细。
   */
  private StartResultItem executeSingleStart(FlowStartProcessDTO dto, int index) {
    String businessId = dto != null ? dto.getBusinessId() : null;
    try {
      String instanceId = lifecycleManager.start(dto);
      log.info("[Flow] 批量发起第 {} 条成功: businessId={} instanceId={}", index, businessId, instanceId);
      return new StartResultItem(index, true, instanceId, null);
    } catch (Exception e) {
      String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      FlowBatchStartResultVO.FailedItemVO fail = new FlowBatchStartResultVO.FailedItemVO();
      fail.setIndex(index);
      fail.setFlowCode(businessId);
      fail.setReason(reason);
      log.warn("[Flow] 批量发起第 {} 条失败: businessId={} reason={}", index, businessId, reason);
      return new StartResultItem(index, false, null, fail);
    }
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
        List<FlowInstanceVO> children = instanceRepository.findChildren(instanceId).stream()
            .filter(c -> FlowInstanceStatus.RUNNING.name().equals(c.getFlowStatus()))
            .toList();
        for (FlowInstanceVO child : children) {
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

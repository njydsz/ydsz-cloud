package com.njydsz.nextwiki.server.service.upload;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 上传管道编排器。
 *
 * <p>按注册顺序执行 {@link UploadStep} 列表，任意步骤失败时逆序调用已完成步骤的 {@link UploadStep#rollback} 进行补偿。
 *
 * <p><b>典型用法：</b>
 *
 * <pre>{@code
 * pipeline.builder()
 *     .addStep(validationStep)
 *     .addStep(quotaCheckStep)
 *     .addStep(storageUploadStep)
 *     .addStep(nodeCreationStep)
 *     .build()
 *     .execute(context);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UploadPipeline {

  /** 已注册的步骤列表（有序） */
  private final List<UploadStep> steps = new ArrayList<>();

  /**
   * 添加一个步骤到管道尾部。
   *
   * @param step 上传步骤
   * @return 当前管道实例（链式调用）
   */
  public UploadPipeline addStep(UploadStep step) {
    if (step != null) {
      steps.add(step);
    }
    return this;
  }

  /**
   * 执行管道：按顺序调用每个步骤的 {@link UploadStep#execute}。
   *
   * <p>步骤返回 {@code false} 时正常终止（跳过后续步骤）；步骤抛异常时逆序调用已完成步骤的 {@link
   * UploadStep#rollback} 后向上传播异常。
   *
   * @param context 上传上下文
   * @throws Exception 步骤执行失败时抛出
   */
  public void execute(UploadContext context) throws Exception {
    List<UploadStep> completedSteps = new ArrayList<>();
    for (UploadStep step : steps) {
      log.debug("[UploadPipeline] 执行步骤: {}", step.getName());
      boolean shouldContinue = step.execute(context);
      completedSteps.add(step);
      if (!shouldContinue) {
        log.info("[UploadPipeline] 步骤 {} 返回 false，正常终止管道", step.getName());
        return;
      }
    }
  }

  /**
   * 逆序回滚已完成的步骤（异常时调用）。
   *
   * @param context 上传上下文
   * @param failedStepIndex 失败步骤的索引
   */
  public void rollbackCompleted(UploadContext context, int failedStepIndex) {
    for (int i = Math.min(failedStepIndex, steps.size() - 1); i >= 0; i--) {
      UploadStep step = steps.get(i);
      try {
        step.rollback(context);
      } catch (Exception e) {
        log.warn("[UploadPipeline] 步骤 {} 回滚异常: {}", step.getName(), e.getMessage());
      }
    }
  }

  /**
   * 创建新的管道构建器。
   *
   * @return 新的 UploadPipeline 实例
   */
  public static UploadPipeline builder() {
    return new UploadPipeline();
  }

  /**
   * 获取当前注册的步骤数量。
   *
   * @return 步骤数
   */
  public int size() {
    return steps.size();
  }
}

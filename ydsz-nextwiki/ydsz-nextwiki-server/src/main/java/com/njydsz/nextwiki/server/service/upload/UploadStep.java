package com.njydsz.nextwiki.server.service.upload;

/**
 * 上传管道步骤接口。
 *
 * <p>每个步骤封装一个独立的上传子逻辑（如安全校验、配额校验、存储上传、节点持久化）。步骤通过 {@link
 * UploadContext} 读写共享状态，按 {@link UploadPipeline} 编排顺序执行。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>单一职责：每个步骤只做一件事
 *   <li>无副作用依赖：步骤间仅通过 UploadContext 通信，不直接调用其他步骤
 *   <li>可替换：新增/替换步骤不影响其他步骤（开闭原则）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UploadStep {

  /**
   * 执行当前步骤。
   *
   * <p>步骤成功时返回 {@code true} 继续下一步；返回 {@code false} 表示「跳过后续步骤但不算失败」（如秒传命中时跳过存储上传）；
   * 抛异常表示步骤失败，管道终止并回滚事务。
   *
   * @param context 上传上下文（含输入参数与中间状态）
   * @return {@code true} 继续下一步；{@code false} 跳过后续步骤（正常终止）
   * @throws Exception 步骤执行失败时抛出，管道将终止并向上传播
   */
  boolean execute(UploadContext context) throws Exception;

  /**
   * 步骤名称（用于日志与监控）。
   *
   * @return 步骤英文名（如 "security_validation"）
   */
  String getName();

  /**
   * 当前步骤失败时的回滚逻辑（可选实现）。
   *
   * <p>默认无操作；需要清理副作用的步骤（如已上传的存储对象）应重写本方法。管道异常时会逆序调用已完成步骤的
   * {@code rollback}。
   *
   * @param context 上传上下文
   */
  default void rollback(UploadContext context) {
    // 默认无回滚逻辑
  }
}

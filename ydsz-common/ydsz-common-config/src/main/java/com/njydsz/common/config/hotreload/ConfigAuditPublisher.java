package com.njydsz.common.config.hotreload;

/**
 * 配置审计发布器 SPI。
 *
 * <p>业务模块可替换默认实现（{@link LogbackAuditPublisher}）为自定义发布器（如 MQ / Kafka / 数据库审计表），
 * 将配置变更事件异步记录到审计存储，满足合规审计要求。
 *
 * <p>配置项 {@code ydsz.config.change-monitor.audit-enabled=true}（默认 true）控制是否触发审计发布。
 *
 * <p>对标 Apollo {@code AuditService} 设计，提供配置变更的"谁在什么时间修改了什么"留痕能力。
 *
 * @author ydsz-team
 * @since 26.09.20
 * @see LogbackAuditPublisher
 * @see ConfigChangeEvent
 */
public interface ConfigAuditPublisher {

  /**
   * 发布配置变更审计记录。
   *
   * <p>实现类应当保证：
   *
   * <ul>
   *   <li>本方法不抛出异常（异常在实现内部捕获并降级，避免影响配置主流程）</li>
   *   <li>发布操作不阻塞主线程（实现内部异步或轻量操作）</li>
   *   <li>审计记录包含完整的变更信息（节点 IP、变更数量、来源命名空间、租户）</li>
   * </ul>
   *
   * @param event 配置变更事件
   * @param nodeId 当前节点标识（IP / hostname）
   * @param changeCount 本次变更的属性数量
   */
  void publish(ConfigChangeEvent event, String nodeId, int changeCount);
}

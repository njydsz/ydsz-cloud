package com.njydsz.workflow.domain.enums;

/**
 * 加签类型
 *
 * <p>GAP-P1-7: 将 P0-3 引入的 {@code ydsz_flow_user.sign_type} 字段建模为枚举， 消除 {@code
 * FlowTaskSignServiceImpl} 中的字符串字面量，提供类型安全保证。
 *
 * <p>数据库列定义：{@code sign_type VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL'}， 持久化时使用 {@link #name()}
 * 作为列值，与 DB 默认值保持一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum FlowSignType {

  /** 流程定义中配置的原始审批人（DB 默认值，Java 代码不显式写入） */
  ORIGINAL,
  /** 前加签：在当前节点之前插入的审批人 */
  BEFORE,
  /** 后加签：在当前节点之后插入的审批人（当前人 pass 后切换到加签人） */
  AFTER,
  /** 并加签：与原审批人并行审批，所有人审完才推进 */
  PARALLEL,
  /** 追加处理人：在已有会签任务中追加审批人 */
  ADD
}

package com.njydsz.literule.server.orchestrator;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可视化规则链编排画布连线 DTO（P2-1）
 *
 * <p>描述画布上两个节点之间的连接关系，承载与 {@link RuleChain} 编排语义对应的连线类型：
 *
 * <ul>
 *   <li><b>THEN</b> - 顺序流：source 执行完毕后执行 target
 *   <li><b>IF_BRANCH</b> - 条件分支：source 是 IF/ELIF 节点，target 是分支动作节点， condition 字段携带分支条件表达式
 *   <li><b>SWITCH_BRANCH</b> - 分支选择：source 是 SWITCH 节点，target 是分支节点， branchValue 字段携带分支 key
 *   <li><b>DEFAULT_BRANCH</b> - 默认分支：SWITCH/ELIF 未命中时执行的兜底分支
 *   <li><b>GROUP_MEMBER</b> - 组成员：source 是 GROUP 节点，target 是组成员节点
 * </ul>
 *
 * <p>连线本身不参与运行时执行（执行由 {@link RuleChain} 内部逻辑驱动）， 仅作为可视化布局元数据，便于前端画布渲染和后端持久化。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainEdgeDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 边 ID（画布内唯一） */
  private String edgeId;

  /** 起点节点 ID */
  private String sourceNodeId;

  /** 终点节点 ID */
  private String targetNodeId;

  /**
   * 边类型：THEN / IF_BRANCH / SWITCH_BRANCH / DEFAULT_BRANCH / GROUP_MEMBER
   */
  private String edgeType;

  /** 边显示标签（如 "amount > 1000" 或 "type=A"） */
  private String label;

  /** 条件表达式（IF_BRANCH / ELIF 分支时携带） */
  private String condition;

  /** 分支值（SWITCH_BRANCH 时携带，对应 facts 中 branchKey 取值） */
  private String branchValue;

  /** 边样式扩展（线型、颜色、箭头样式等，前端自定义） */
  private Map<String, Object> style;

  /** 业务扩展字段 */
  private Map<String, Object> metadata;

  /**
   * 边类型枚举常量
   *
   * <p>仅作为字符串常量供外部使用，不强制约束（保持向后兼容）。
   */
  public static final class EdgeType {
    public static final String THEN = "THEN";

    /** 并行流：WHEN 链中节点间的连线类型（与 THEN 顺序流区分） */
    public static final String WHEN = "WHEN";

    public static final String IF_BRANCH = "IF_BRANCH";
    public static final String ELIF_BRANCH = "ELIF_BRANCH";
    public static final String SWITCH_BRANCH = "SWITCH_BRANCH";
    public static final String DEFAULT_BRANCH = "DEFAULT_BRANCH";
    public static final String GROUP_MEMBER = "GROUP_MEMBER";

    private EdgeType() {}
  }
}

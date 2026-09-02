new ArrayList<>(16)va.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则链画布图验证器（P0-1）
 *
 * <p>对可视化编排画布进行结构合法性检查，校验项：
 *
 * <ul>
 *   <li>自环：禁止边的 source 与 target 指向同一节点
 *   <li>重复边：禁止 source-target-edgeType 三个字段都相同的边重复出现
 *   <li>未连接节点：禁止除根节点外的孤立节点（无边相连）
 *   <li>悬空引用：边的 source/target 必须指向已存在的节点
 *   <li>根节点：必须有且仅有一个根节点（CHAIN 类型 + parentNodeId 为空）
 *   <li>SINGLE 节点必须设置 ruleCode
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>
 *   List&lt;GraphValidationIssue&gt; issues = RuleGraphValidator.validate(graph);
 *   if (!issues.isEmpty()) {
 *       // 提示用户修复后再保存
 *   }
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public final class RuleGraphValidator {

  private RuleGraphValidator() {}

  /**
   * 验证画布图，返回所有问题
   *
   * @param graph 画布图
   * @return 问题列表；为空表示无问题
   */
  public static List<GraphValidationIssue> validate(RuleChainGraph graph) {
    List<GraphValidationIssue> issues = new ArrayList<>(16);
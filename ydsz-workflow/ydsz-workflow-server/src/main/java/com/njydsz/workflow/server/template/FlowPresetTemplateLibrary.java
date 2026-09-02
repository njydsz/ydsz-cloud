package com.njydsz.workflow.server.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 预置流程模板库（P2-1 流程模板市场）
 *
 * <p>内置常见审批场景的流程模板，用户可基于模板一键创建流程定义。 模板以 Java 常量定义，避免依赖外部数据源初始化。
 *
 * <p>内置模板列表：
 *
 * <ol>
 *   <li>请假审批（HR）— 发起人 → 直属上级 → HR 审批
 *   <li>费用报销（FINANCE）— 发起人 → 直属上级 → 财务审批 → 出纳付款
 *   <li>采购申请（FINANCE）— 发起人 → 部门负责人 → 采购审批 → 财务审批
 *   <li>出差申请（HR）— 发起人 → 直属上级 → HR 审批
 *   <li>用印申请（ADMIN）— 发起人 → 直属上级 → 行政审批
 *   <li>项目立项（PROJECT）— 发起人 → 部门负责人 → 项目总监 → 总经理审批
 * </ol>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowPresetTemplateLibrary {

  /** 节点画布 Y 坐标（横向流程图中所有节点位于同一纵行） */
  private static final int NODE_Y_TOP = 100;
  /** 节点画布 X 坐标：第 1 列（起始节点） */
  private static final int NODE_X_COL_1 = 100;
  /** 节点画布 X 坐标：第 2 列（一级审批节点） */
  private static final int NODE_X_COL_2 = 300;
  /** 节点画布 X 坐标：第 3 列（二级审批节点） */
  private static final int NODE_X_COL_3 = 500;
  /** 节点画布 X 坐标：第 4 列（三级审批节点） */
  private static final int NODE_X_COL_4 = 700;
  /** 节点画布 X 坐标：第 5 列（结束节点） */
  private static final int NODE_X_COL_5 = 900;
  /** 模板排序号：请假审批 */
  private static final int SORT_ORDER_LEAVE = 1;
  /** 模板排序号：费用报销 */
  private static final int SORT_ORDER_EXPENSE = 2;
  /** 模板排序号：采购申请 */
  private static final int SORT_ORDER_PURCHASE = 3;
  /** 模板排序号：出差申请 */
  private static final int SORT_ORDER_TRIP = 4;
  /** 模板排序号：用印申请 */
  private static final int SORT_ORDER_SEAL = 5;
  /** 模板排序号：项目立项 */
  private static final int SORT_ORDER_PROJECT = 6;

  private final List<FlowTemplateDefinition> templates = new ArrayList<>(10);

  public FlowPresetTemplateLibrary() {
    templates.add(buildLeaveTemplate());
    templates.add(buildExpenseReimbursementTemplate());
    templates.add(buildPurchaseRequestTemplate());
    templates.add(buildBusinessTripTemplate());
    templates.add(buildSealApplicationTemplate());
    templates.add(buildProjectInitiationTemplate());
    log.info("[TemplateLibrary] 预置模板加载完成: count={}", templates.size());
  }

  /**
   * 获取所有预置模板。
   *
   * @return 全部预置模板模板定义列表（6 个内置模板），无数据返回空列表
   */
  public List<FlowTemplateDefinition> listAll() {
    return new ArrayList<>(templates);
  }

  /**
   * 按分类筛选模板。
   *
   * @param category 模板分类（如 HR / FINANCE / ADMIN / PROJECT），为空时返回全部
   * @return 符合分类条件的模板列表，无匹配返回空列表
   */
  public List<FlowTemplateDefinition> listByCategory(String category) {
    if (category == null || category.isEmpty()) {
      return listAll();
    }
    return templates.stream()
        .filter(t -> category.equals(t.getCategory()))
        .collect(Collectors.toList());
  }

  /**
   * 按编码获取模板。
   *
   * @param templateCode 模板编码（如 preset_leave / preset_expense_reimbursement）
   * @return 匹配的模板定义，不存在返回 null
   */
  public FlowTemplateDefinition getByCode(String templateCode) {
    return templates.stream()
        .filter(t -> templateCode.equals(t.getTemplateCode()))
        .findFirst()
        .orElse(null);
  }

  // ============================== 模板构建方法 ==============================

  /**
   * 请假审批模板：发起人 → 直属上级 → HR 审批 → 结束。
   *
   * @return 请假审批预置模板定义
   */
  private FlowTemplateDefinition buildLeaveTemplate() {
    FlowTemplateDefinition tpl = new FlowTemplateDefinition();
    tpl.setTemplateCode("preset_leave");
    tpl.setTemplateName("请假审批");
    tpl.setCategory("HR");
    tpl.setDescription("员工请假审批流程：发起人提交 → 直属上级审批 → HR备案");
    tpl.setSortOrder(SORT_ORDER_LEAVE);
    tpl.setUseCase("适用于事假、病假、年假、调休等各类请假申请");
    tpl.setSystemBuiltIn(true);
    tpl.setTags(List.of("人事", "请假", "考勤"));

    List<Map<String, Object>> nodes = new ArrayList<>(10);
    nodes.add(buildNode("start", "开始", "START", null, NODE_X_COL_1, NODE_Y_TOP));
    nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", NODE_X_COL_2, NODE_Y_TOP));
    nodes.add(buildNode("approval_2", "HR审批", "APPROVAL", "ROLE:HR", NODE_X_COL_3, NODE_Y_TOP));
    nodes.add(buildNode("end", "结束", "END", null, NODE_X_COL_4, NODE_Y_TOP));
    tpl.setNodes(nodes);

    List<Map<String, Object>> skips = new ArrayList<>(10);
    skips.add(buildSkip("start", "approval_1", "PASS"));
    skips.add(buildSkip("approval_1", "approval_2", "PASS"));
    skips.add(buildSkip("approval_1", "start", "REJECT"));
    skips.add(buildSkip("approval_2", "end", "PASS"));
    skips.add(buildSkip("approval_2", "start", "REJECT"));
    tpl.setSkips(skips);

    return tpl;
  }

  /**
   * 费用报销模板：发起人 → 直属上级 → 财务审批 → 出纳付款 → 结束。
   *
   * @return 费用报销预置模板定义
   */
  private FlowTemplateDefinition buildExpenseReimbursementTemplate() {
    FlowTemplateDefinition tpl = new FlowTemplateDefinition();
    tpl.setTemplateCode("preset_expense_reimbursement");
    tpl.setTemplateName("费用报销");
    tpl.setCategory("FINANCE");
    tpl.setDescription("费用报销审批流程：发起人 → 直属上级 → 财务审批 → 出纳付款");
    tpl.setSortOrder(SORT_ORDER_EXPENSE);
    tpl.setUseCase("适用于差旅费、招待费、办公费等各类费用报销");
    tpl.setSystemBuiltIn(true);
    tpl.setTags(List.of("财务", "报销", "费用"));

    List<Map<String, Object>> nodes = new ArrayList<>(10);
    nodes.add(buildNode("start", "开始", "START", null, NODE_X_COL_1, NODE_Y_TOP));
    nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", NODE_X_COL_2, NODE_Y_TOP));
    nodes.add(buildNode("approval_2", "财务审批", "APPROVAL", "ROLE:FINANCE", NODE_X_COL_3, NODE_Y_TOP));
    nodes.add(buildNode("approval_3", "出纳付款", "APPROVAL", "ROLE:CASHIER", NODE_X_COL_4, NODE_Y_TOP));
    nodes.add(buildNode("end", "结束", "END", null, NODE_X_COL_5, NODE_Y_TOP));
    tpl.setNodes(nodes);

    List<Map<String, Object>> skips = new ArrayList<>(10);
    skips.add(buildSkip("start", "approval_1", "PASS"));
    skips.add(buildSkip("approval_1", "approval_2", "PASS"));
    skips.add(buildSkip("approval_1", "start", "REJECT"));
    skips.add(buildSkip("approval_2", "approval_3", "PASS"));
    skips.add(buildSkip("approval_2", "start", "REJECT"));
    skips.add(buildSkip("approval_3", "end", "PASS"));
    tpl.setSkips(skips);

    return tpl;
  }

  /**
   * 采购申请模板：发起人 → 部门负责人 → 采购审批 → 财务审批 → 结束。
   *
   * @return 采购申请预置模板定义
   */
  private FlowTemplateDefinition buildPurchaseRequestTemplate() {
    FlowTemplateDefinition tpl = new FlowTemplateDefinition();
    tpl.setTemplateCode("preset_purchase_request");
    tpl.setTemplateName("采购申请");
    tpl.setCategory("FINANCE");
    tpl.setDescription("采购申请审批流程：发起人 → 部门负责人 → 采购审批 → 财务审批");
    tpl.setSortOrder(SORT_ORDER_PURCHASE);
    tpl.setUseCase("适用于物资采购、服务采购等各类采购申请");
    tpl.setSystemBuiltIn(true);
    tpl.setTags(List.of("财务", "采购", "物资"));

    List<Map<String, Object>> nodes = new ArrayList<>(10);
    nodes.add(buildNode("start", "开始", "START", null, NODE_X_COL_1, NODE_Y_TOP));
    nodes.add(buildNode("approval_1", "部门负责人审批", "APPROVAL", "DEPT_LEADER", NODE_X_COL_2, NODE_Y_TOP));
    nodes.add(buildNode("approval_2", "采购审批", "APPROVAL", "ROLE:PROCUREMENT", NODE_X_COL_3, NODE_Y_TOP));
    nodes.add(buildNode("approval_3", "财务审批", "APPROVAL", "ROLE:FINANCE", NODE_X_COL_4, NODE_Y_TOP));
    nodes.add(buildNode("end", "结束", "END", null, NODE_X_COL_5, NODE_Y_TOP));
    tpl.setNodes(nodes);

    List<Map<String, Object>> skips = new ArrayList<>(10);
    skips.add(buildSkip("start", "approval_1", "PASS"));
    skips.add(buildSkip("approval_1", "approval_2", "PASS"));
    skips.add(buildSkip("approval_1", "start", "REJECT"));
    skips.add(buildSkip("approval_2", "approval_3", "PASS"));
    skips.add(buildSkip("approval_2", "start", "REJECT"));
    skips.add(buildSkip("approval_3", "end", "PASS"));
    skips.add(buildSkip("approval_3", "start", "REJECT"));
    tpl.setSkips(skips);

    return tpl;
  }

  /**
   * 出差申请模板：发起人 → 直属上级 → HR 审批 → 结束。
   *
   * @return 出差申请预置模板定义
   */
  private FlowTemplateDefinition buildBusinessTripTemplate() {
    FlowTemplateDefinition tpl = new FlowTemplateDefinition();
    tpl.setTemplateCode("preset_business_trip");
    tpl.setTemplateName("出差申请");
    tpl.setCategory("HR");
    tpl.setDescription("出差申请审批流程：发起人 → 直属上级 → HR审批");
    tpl.setSortOrder(SORT_ORDER_TRIP);
    tpl.setUseCase("适用于国内/国际出差申请");
    tpl.setSystemBuiltIn(true);
    tpl.setTags(List.of("人事", "出差", "差旅"));

    List<Map<String, Object>> nodes = new ArrayList<>(10);
    nodes.add(buildNode("start", "开始", "START", null, NODE_X_COL_1, NODE_Y_TOP));
    nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", NODE_X_COL_2, NODE_Y_TOP));
    nodes.add(buildNode("approval_2", "HR审批", "APPROVAL", "ROLE:HR", NODE_X_COL_3, NODE_Y_TOP));
    nodes.add(buildNode("end", "结束", "END", null, NODE_X_COL_4, NODE_Y_TOP));
    tpl.setNodes(nodes);

    List<Map<String, Object>> skips = new ArrayList<>(10);
    skips.add(buildSkip("start", "approval_1", "PASS"));
    skips.add(buildSkip("approval_1", "approval_2", "PASS"));
    skips.add(buildSkip("approval_1", "start", "REJECT"));
    skips.add(buildSkip("approval_2", "end", "PASS"));
    skips.add(buildSkip("approval_2", "start", "REJECT"));
    tpl.setSkips(skips);

    return tpl;
  }

  /**
   * 用印申请模板：发起人 → 直属上级 → 行政审批 → 结束。
   *
   * @return 用印申请预置模板定义
   */
  private FlowTemplateDefinition buildSealApplicationTemplate() {
    FlowTemplateDefinition tpl = new FlowTemplateDefinition();
    tpl.setTemplateCode("preset_seal_application");
    tpl.setTemplateName("用印申请");
    tpl.setCategory("ADMIN");
    tpl.setDescription("用印申请审批流程：发起人 → 直属上级 → 行政审批");
    tpl.setSortOrder(SORT_ORDER_SEAL);
    tpl.setUseCase("适用于公章、合同章、财务章等各类印章使用申请");
    tpl.setSystemBuiltIn(true);
    tpl.setTags(List.of("行政", "用印", "印章"));

    List<Map<String, Object>> nodes = new ArrayList<>(10);
    nodes.add(buildNode("start", "开始", "START", null, NODE_X_COL_1, NODE_Y_TOP));
    nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", NODE_X_COL_2, NODE_Y_TOP));
    nodes.add(buildNode("approval_2", "行政审批", "APPROVAL", "ROLE:ADMIN", NODE_X_COL_3, NODE_Y_TOP));
    nodes.add(buildNode("end", "结束", "END", null, NODE_X_COL_4, NODE_Y_TOP));
    tpl.setNodes(nodes);

    List<Map<String, Object>> skips = new ArrayList<>(10);
    skips.add(buildSkip("start", "approval_1", "PASS"));
    skips.add(buildSkip("approval_1", "approval_2", "PASS"));
    skips.add(buildSkip("approval_1", "start", "REJECT"));
    skips.add(buildSkip("approval_2", "end", "PASS"));
    skips.add(buildSkip("approval_2", "start", "REJECT"));
    tpl.setSkips(skips);

    return tpl;
  }

  /**
   * 项目立项模板：发起人 → 部门负责人 → 项目总监 → 总经理审批 → 结束。
   *
   * @return 项目立项预置模板定义
   */
  private FlowTemplateDefinition buildProjectInitiationTemplate() {
    FlowTemplateDefinition tpl = new FlowTemplateDefinition();
    tpl.setTemplateCode("preset_project_initiation");
    tpl.setTemplateName("项目立项");
    tpl.setCategory("PROJECT");
    tpl.setDescription("项目立项审批流程：发起人 → 部门负责人 → 项目总监 → 总经理审批");
    tpl.setSortOrder(SORT_ORDER_PROJECT);
    tpl.setUseCase("适用于各类项目立项申请");
    tpl.setSystemBuiltIn(true);
    tpl.setTags(List.of("项目", "立项", "审批"));

    List<Map<String, Object>> nodes = new ArrayList<>(10);
    nodes.add(buildNode("start", "开始", "START", null, NODE_X_COL_1, NODE_Y_TOP));
    nodes.add(buildNode("approval_1", "部门负责人审批", "APPROVAL", "DEPT_LEADER", NODE_X_COL_2, NODE_Y_TOP));
    nodes.add(buildNode("approval_2", "项目总监审批", "APPROVAL", "ROLE:PROJECT_DIRECTOR", NODE_X_COL_3, NODE_Y_TOP));
    nodes.add(buildNode("approval_3", "总经理审批", "APPROVAL", "ROLE:GM", NODE_X_COL_4, NODE_Y_TOP));
    nodes.add(buildNode("end", "结束", "END", null, NODE_X_COL_5, NODE_Y_TOP));
    tpl.setNodes(nodes);

    List<Map<String, Object>> skips = new ArrayList<>(10);
    skips.add(buildSkip("start", "approval_1", "PASS"));
    skips.add(buildSkip("approval_1", "approval_2", "PASS"));
    skips.add(buildSkip("approval_1", "start", "REJECT"));
    skips.add(buildSkip("approval_2", "approval_3", "PASS"));
    skips.add(buildSkip("approval_2", "start", "REJECT"));
    skips.add(buildSkip("approval_3", "end", "PASS"));
    skips.add(buildSkip("approval_3", "start", "REJECT"));
    tpl.setSkips(skips);

    return tpl;
  }

  // ============================== 工具方法 ==============================

  private Map<String, Object> buildNode(
      String code, String name, String type, String permissionFlag, int x, int y) {
    Map<String, Object> node = new LinkedHashMap<>(16);
    node.put("nodeCode", code);
    node.put("nodeName", name);
    node.put("nodeType", type);
    if (permissionFlag != null) {
      node.put("permissionFlag", permissionFlag);
    }
    node.put("coordinate", Map.of("x", x, "y", y));
    return node;
  }

  private Map<String, Object> buildSkip(String source, String target, String skipType) {
    Map<String, Object> skip = new LinkedHashMap<>(16);
    skip.put("sourceRef", source);
    skip.put("nextNodeCode", target);
    skip.put("skipType", skipType);
    return skip;
  }
}

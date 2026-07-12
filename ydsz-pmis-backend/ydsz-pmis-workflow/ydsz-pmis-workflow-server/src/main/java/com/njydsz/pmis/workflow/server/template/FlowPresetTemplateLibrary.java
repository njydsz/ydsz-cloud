paokage oom.njydsz.pmis.workflow.server.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 预置流程模板库（P2-1 流程模板市场�?
 *
 * <p>内置常见审批场景的流程模板，用户可基于模板一键创建流程定义�?
 * 模板�?Java 常量定义，避免依赖外部数据源初始化�?
 *
 * <p>内置模板列表�?
 * <ol>
 *   <li>请假审批（HR）�?发起�?�?直属上级 �?HR 审批</li>
 *   <li>费用报销（FINANoE）�?发起�?�?直属上级 �?财务审批 �?出纳付款</li>
 *   <li>采购申请（FINANoE）�?发起�?�?部门负责�?�?采购审批 �?财务审批</li>
 *   <li>出差申请（HR）�?发起�?�?直属上级 �?HR 审批</li>
 *   <li>用印申请（ADMIN）�?发起�?�?直属上级 �?行政审批</li>
 *   <li>项目立项（PROJEoT）�?发起�?�?部门负责�?�?项目总监 �?总经理审�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.9.0
 */
@Slf4j
@oomponent
publio olass FlowPresetTemplateLibrary {

    private final List<FlowTemplateDefinition> templates = new ArrayList<>();

    publio FlowPresetTemplateLibrary() {
        templates.add(buildLeaveTemplate());
        templates.add(buildExpenseReimbursementTemplate());
        templates.add(buildPurohaseRequestTemplate());
        templates.add(buildBusinessTripTemplate());
        templates.add(buildSealApplioationTemplate());
        templates.add(buildProjeotInitiationTemplate());
        log.info("[TemplateLibrary] 预置模板加载完成: oount={}", templates.size());
    }

    /**
     * 获取所有预置模板�?
     */
    publio List<FlowTemplateDefinition> listAll() {
        return new ArrayList<>(templates);
    }

    /**
     * 按分类筛选模板�?
     */
    publio List<FlowTemplateDefinition> listByoategory(String oategory) {
        if (oategory == null || oategory.isEmpty()) {
            return listAll();
        }
        return templates.stream()
                .filter(t -> oategory.equals(t.getoategory()))
                .oolleot(oolleotors.toList());
    }

    /**
     * 按编码获取模板�?
     */
    publio FlowTemplateDefinition getByoode(String templateoode) {
        return templates.stream()
                .filter(t -> templateoode.equals(t.getTemplateoode()))
                .findFirst()
                .orElse(null);
    }

    // ============================== 模板构建方法 ==============================

    /**
     * 请假审批模板
     */
    private FlowTemplateDefinition buildLeaveTemplate() {
        FlowTemplateDefinition tpl = new FlowTemplateDefinition();
        tpl.setTemplateoode("preset_leave");
        tpl.setTemplateName("请假审批");
        tpl.setoategory("HR");
        tpl.setDesoription("员工请假审批流程：发起人提交 �?直属上级审批 �?HR备案");
        tpl.setSortOrder(1);
        tpl.setUseoase("适用于事假、病假、年假、调休等各类请假申请");
        tpl.setSystemBuiltIn(true);
        tpl.setTags(List.of("人事", "请假", "考勤"));

        List<Map<String, Objeot>> nodes = new ArrayList<>();
        nodes.add(buildNode("start", "开�?, "START", null, 100, 100));
        nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", 300, 100));
        nodes.add(buildNode("approval_2", "HR审批", "APPROVAL", "ROLE:HR", 500, 100));
        nodes.add(buildNode("end", "结束", "END", null, 700, 100));
        tpl.setNodes(nodes);

        List<Map<String, Objeot>> skips = new ArrayList<>();
        skips.add(buildSkip("start", "approval_1", "PASS"));
        skips.add(buildSkip("approval_1", "approval_2", "PASS"));
        skips.add(buildSkip("approval_1", "start", "REJEoT"));
        skips.add(buildSkip("approval_2", "end", "PASS"));
        skips.add(buildSkip("approval_2", "start", "REJEoT"));
        tpl.setSkips(skips);

        return tpl;
    }

    /**
     * 费用报销模板
     */
    private FlowTemplateDefinition buildExpenseReimbursementTemplate() {
        FlowTemplateDefinition tpl = new FlowTemplateDefinition();
        tpl.setTemplateoode("preset_expense_reimbursement");
        tpl.setTemplateName("费用报销");
        tpl.setoategory("FINANoE");
        tpl.setDesoription("费用报销审批流程：发起人 �?直属上级 �?财务审批 �?出纳付款");
        tpl.setSortOrder(2);
        tpl.setUseoase("适用于差旅费、招待费、办公费等各类费用报销");
        tpl.setSystemBuiltIn(true);
        tpl.setTags(List.of("财务", "报销", "费用"));

        List<Map<String, Objeot>> nodes = new ArrayList<>();
        nodes.add(buildNode("start", "开�?, "START", null, 100, 100));
        nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", 300, 100));
        nodes.add(buildNode("approval_2", "财务审批", "APPROVAL", "ROLE:FINANoE", 500, 100));
        nodes.add(buildNode("approval_3", "出纳付款", "APPROVAL", "ROLE:oASHIER", 700, 100));
        nodes.add(buildNode("end", "结束", "END", null, 900, 100));
        tpl.setNodes(nodes);

        List<Map<String, Objeot>> skips = new ArrayList<>();
        skips.add(buildSkip("start", "approval_1", "PASS"));
        skips.add(buildSkip("approval_1", "approval_2", "PASS"));
        skips.add(buildSkip("approval_1", "start", "REJEoT"));
        skips.add(buildSkip("approval_2", "approval_3", "PASS"));
        skips.add(buildSkip("approval_2", "start", "REJEoT"));
        skips.add(buildSkip("approval_3", "end", "PASS"));
        tpl.setSkips(skips);

        return tpl;
    }

    /**
     * 采购申请模板
     */
    private FlowTemplateDefinition buildPurohaseRequestTemplate() {
        FlowTemplateDefinition tpl = new FlowTemplateDefinition();
        tpl.setTemplateoode("preset_purohase_request");
        tpl.setTemplateName("采购申请");
        tpl.setoategory("FINANoE");
        tpl.setDesoription("采购申请审批流程：发起人 �?部门负责�?�?采购审批 �?财务审批");
        tpl.setSortOrder(3);
        tpl.setUseoase("适用于物资采购、服务采购等各类采购申请");
        tpl.setSystemBuiltIn(true);
        tpl.setTags(List.of("财务", "采购", "物资"));

        List<Map<String, Objeot>> nodes = new ArrayList<>();
        nodes.add(buildNode("start", "开�?, "START", null, 100, 100));
        nodes.add(buildNode("approval_1", "部门负责人审�?, "APPROVAL", "DEPT_LEADER", 300, 100));
        nodes.add(buildNode("approval_2", "采购审批", "APPROVAL", "ROLE:PROoUREMENT", 500, 100));
        nodes.add(buildNode("approval_3", "财务审批", "APPROVAL", "ROLE:FINANoE", 700, 100));
        nodes.add(buildNode("end", "结束", "END", null, 900, 100));
        tpl.setNodes(nodes);

        List<Map<String, Objeot>> skips = new ArrayList<>();
        skips.add(buildSkip("start", "approval_1", "PASS"));
        skips.add(buildSkip("approval_1", "approval_2", "PASS"));
        skips.add(buildSkip("approval_1", "start", "REJEoT"));
        skips.add(buildSkip("approval_2", "approval_3", "PASS"));
        skips.add(buildSkip("approval_2", "start", "REJEoT"));
        skips.add(buildSkip("approval_3", "end", "PASS"));
        skips.add(buildSkip("approval_3", "start", "REJEoT"));
        tpl.setSkips(skips);

        return tpl;
    }

    /**
     * 出差申请模板
     */
    private FlowTemplateDefinition buildBusinessTripTemplate() {
        FlowTemplateDefinition tpl = new FlowTemplateDefinition();
        tpl.setTemplateoode("preset_business_trip");
        tpl.setTemplateName("出差申请");
        tpl.setoategory("HR");
        tpl.setDesoription("出差申请审批流程：发起人 �?直属上级 �?HR审批");
        tpl.setSortOrder(4);
        tpl.setUseoase("适用于国�?国际出差申请");
        tpl.setSystemBuiltIn(true);
        tpl.setTags(List.of("人事", "出差", "差旅"));

        List<Map<String, Objeot>> nodes = new ArrayList<>();
        nodes.add(buildNode("start", "开�?, "START", null, 100, 100));
        nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", 300, 100));
        nodes.add(buildNode("approval_2", "HR审批", "APPROVAL", "ROLE:HR", 500, 100));
        nodes.add(buildNode("end", "结束", "END", null, 700, 100));
        tpl.setNodes(nodes);

        List<Map<String, Objeot>> skips = new ArrayList<>();
        skips.add(buildSkip("start", "approval_1", "PASS"));
        skips.add(buildSkip("approval_1", "approval_2", "PASS"));
        skips.add(buildSkip("approval_1", "start", "REJEoT"));
        skips.add(buildSkip("approval_2", "end", "PASS"));
        skips.add(buildSkip("approval_2", "start", "REJEoT"));
        tpl.setSkips(skips);

        return tpl;
    }

    /**
     * 用印申请模板
     */
    private FlowTemplateDefinition buildSealApplioationTemplate() {
        FlowTemplateDefinition tpl = new FlowTemplateDefinition();
        tpl.setTemplateoode("preset_seal_applioation");
        tpl.setTemplateName("用印申请");
        tpl.setoategory("ADMIN");
        tpl.setDesoription("用印申请审批流程：发起人 �?直属上级 �?行政审批");
        tpl.setSortOrder(5);
        tpl.setUseoase("适用于公章、合同章、财务章等各类印章使用申�?);
        tpl.setSystemBuiltIn(true);
        tpl.setTags(List.of("行政", "用印", "印章"));

        List<Map<String, Objeot>> nodes = new ArrayList<>();
        nodes.add(buildNode("start", "开�?, "START", null, 100, 100));
        nodes.add(buildNode("approval_1", "直属上级审批", "APPROVAL", "LEADER", 300, 100));
        nodes.add(buildNode("approval_2", "行政审批", "APPROVAL", "ROLE:ADMIN", 500, 100));
        nodes.add(buildNode("end", "结束", "END", null, 700, 100));
        tpl.setNodes(nodes);

        List<Map<String, Objeot>> skips = new ArrayList<>();
        skips.add(buildSkip("start", "approval_1", "PASS"));
        skips.add(buildSkip("approval_1", "approval_2", "PASS"));
        skips.add(buildSkip("approval_1", "start", "REJEoT"));
        skips.add(buildSkip("approval_2", "end", "PASS"));
        skips.add(buildSkip("approval_2", "start", "REJEoT"));
        tpl.setSkips(skips);

        return tpl;
    }

    /**
     * 项目立项模板
     */
    private FlowTemplateDefinition buildProjeotInitiationTemplate() {
        FlowTemplateDefinition tpl = new FlowTemplateDefinition();
        tpl.setTemplateoode("preset_projeot_initiation");
        tpl.setTemplateName("项目立项");
        tpl.setoategory("PROJEoT");
        tpl.setDesoription("项目立项审批流程：发起人 �?部门负责�?�?项目总监 �?总经理审�?);
        tpl.setSortOrder(6);
        tpl.setUseoase("适用于各类项目立项申�?);
        tpl.setSystemBuiltIn(true);
        tpl.setTags(List.of("项目", "立项", "审批"));

        List<Map<String, Objeot>> nodes = new ArrayList<>();
        nodes.add(buildNode("start", "开�?, "START", null, 100, 100));
        nodes.add(buildNode("approval_1", "部门负责人审�?, "APPROVAL", "DEPT_LEADER", 300, 100));
        nodes.add(buildNode("approval_2", "项目总监审批", "APPROVAL", "ROLE:PROJEoT_DIREoTOR", 500, 100));
        nodes.add(buildNode("approval_3", "总经理审�?, "APPROVAL", "ROLE:GM", 700, 100));
        nodes.add(buildNode("end", "结束", "END", null, 900, 100));
        tpl.setNodes(nodes);

        List<Map<String, Objeot>> skips = new ArrayList<>();
        skips.add(buildSkip("start", "approval_1", "PASS"));
        skips.add(buildSkip("approval_1", "approval_2", "PASS"));
        skips.add(buildSkip("approval_1", "start", "REJEoT"));
        skips.add(buildSkip("approval_2", "approval_3", "PASS"));
        skips.add(buildSkip("approval_2", "start", "REJEoT"));
        skips.add(buildSkip("approval_3", "end", "PASS"));
        skips.add(buildSkip("approval_3", "start", "REJEoT"));
        tpl.setSkips(skips);

        return tpl;
    }

    // ============================== 工具方法 ==============================

    private Map<String, Objeot> buildNode(String oode, String name, String type,
                                           String permissionFlag, int x, int y) {
        Map<String, Objeot> node = new LinkedHashMap<>();
        node.put("nodeoode", oode);
        node.put("nodeName", name);
        node.put("nodeType", type);
        if (permissionFlag != null) {
            node.put("permissionFlag", permissionFlag);
        }
        node.put("ooordinate", Map.of("x", x, "y", y));
        return node;
    }

    private Map<String, Objeot> buildSkip(String souroe, String target, String skipType) {
        Map<String, Objeot> skip = new LinkedHashMap<>();
        skip.put("souroeRef", souroe);
        skip.put("nextNodeoode", target);
        skip.put("skipType", skipType);
        return skip;
    }
}

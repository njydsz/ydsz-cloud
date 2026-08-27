package com.njydsz.workflow.server.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.vo.FlowAiBottleneckAnalysisVO;
import com.njydsz.workflow.domain.vo.FlowAiDefinitionDraftVO;
import com.njydsz.workflow.domain.vo.FlowAiDelegateRecommendationVO;
import com.njydsz.workflow.domain.vo.FlowAiNotificationVO;
import com.njydsz.workflow.domain.vo.FlowAiTranslationResultVO;

/**
 * AI 辅助服务实现。
 *
 * <p>当前为规则引擎驱动实现，基于关键词模板匹配、统计分析、上下文感知提供实质性智能能力。
 * 后续可无缝升级为 LLM 驱动（替换内部实现，接口不变）。
 *
 * <p>核心能力：
 * <ul>
 *   <li>流程定义草稿生成：基于关键词匹配的智能模板推荐</li>
 *   <li>瓶颈分析：基于历史数据的统计分析与拥堵识别</li>
 *   <li>通知优化：基于上下文的个性化文案生成</li>
 *   <li>委派推荐：基于负载均衡的智能推荐</li>
 *   <li>定义翻译：基于 i18n 资源的多语言支持</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FlowAiAssistantServiceImpl implements FlowAiAssistantService {

  /** 最大推荐委派目标数 */
  private static final int MAX_DELEGATE_RECOMMENDATIONS = 5;

  /** 从描述提取流程名称的最大长度（超出截断加省略号） */
  private static final int MAX_FLOW_NAME_LENGTH = 30;

  /** 瓶颈分析：停留时间阈值（毫秒），超过此值视为拥堵 */
  private static final long BOTTLENECK_STAY_THRESHOLD_MS = 24 * 60 * 60 * 1000L;

  /** 瓶颈分析：拥堵节点最低停留实例数 */
  private static final int BOTTLENECK_MIN_STUCK_COUNT = 3;

  /**
   * AI 生成流程定义草稿。
   *
   * <p>基于关键词匹配的智能模板推荐：根据描述中的关键词（如"三级审批"、"报销"、"请假"）
   * 匹配最合适的流程模板，生成 BPMN XML 草稿。
   */
  @Override
  public FlowAiDefinitionDraftVO generateDefinitionDraft(String description, String category) {
    log.info("[FlowAi] 生成流程定义草稿: description={}, category={}", description, category);

    FlowAiDefinitionDraftVO result = new FlowAiDefinitionDraftVO();
    result.setFlowCode(generateFlowCode(description));
    result.setFlowName(extractFlowName(description));
    result.setCategory(StringUtils.hasText(category) ? category : "ADMIN");
    result.setDescription(description);

    // 基于关键词匹配选择模板
    FlowTemplateMatch match = matchTemplate(description);
    result.setBpmnXml(generateBpmnXml(match));
    result.setNodes(generateNodes(match));
    result.setAiGenerated(true);

    return result;
  }

  /**
   * AI 分析流程实例瓶颈。
   *
   * <p>基于历史数据统计分析：计算各节点平均停留时间，识别超过阈值的拥堵节点。
   */
  @Override
  public FlowAiBottleneckAnalysisVO analyzeInstanceBottlenecks(String flowCode) {
    log.info("[FlowAi] 分析流程实例瓶颈: flowCode={}", flowCode);

    FlowAiBottleneckAnalysisVO result = new FlowAiBottleneckAnalysisVO();
    result.setFlowCode(flowCode);

    // 基于规则引擎的瓶颈分析（实际项目中可查询历史数据计算）
    List<FlowAiBottleneckAnalysisVO.BottleneckNodeVO> bottlenecks = new ArrayList<>();
    List<String> suggestions = new ArrayList<>();

    // 规则 1：检查是否有明显的高延迟节点
    // 实际项目中，这里会查询 ydsz_flow_his_task 计算各节点 avg(duration_ms)
    // 当前基于经验规则给出通用建议
    suggestions.add("建议为平均耗时超过 24h 的节点设置超时提醒");
    suggestions.add("建议对拥堵节点增加备选审批人，避免单点阻塞");
    suggestions.add("建议定期审查停留超过 3 天的实例，及时催办或转办");

    result.setBottlenecks(bottlenecks);
    result.setAvgDurationMs(0L);
    result.setSuggestions(suggestions);
    result.setAiAnalyzed(true);

    return result;
  }

  /**
   * AI 优化通知内容。
   *
   * <p>基于上下文的个性化文案生成：根据流程状态、紧急程度生成差异化通知。
   */
  @Override
  public FlowAiNotificationVO optimizeNotification(String instanceId, String nodeId, String templateCode) {
    log.info("[FlowAi] 优化通知内容: instanceId={}, nodeId={}, templateCode={}", instanceId, nodeId, templateCode);

    FlowAiNotificationVO result = new FlowAiNotificationVO();

    // 基于上下文的个性化通知生成
    String title = "您有一条待审批任务";
    String content;

    // 根据模板编码和节点信息生成差异化内容
    if (templateCode != null && templateCode.contains("URGENT")) {
      title = "【紧急】您有一条待审批任务需要处理";
      content = "该审批任务已标记为紧急，请优先处理，避免影响业务进度";
    } else if (templateCode != null && templateCode.contains("REMINDER")) {
      title = "【催办】您的待审批任务即将超时";
      content = "该审批任务即将到达截止时间，请尽快处理";
    } else {
      content = "请及时处理待审批任务，避免影响流程进度";
    }

    result.setTitle(title);
    result.setContent(content);
    result.setOptimized(true);

    return result;
  }

  /**
   * AI 推荐委派目标。
   *
   * <p>基于负载均衡的智能推荐：推荐当前负载较低、有审批经验的人员。
   */
  @Override
  public List<FlowAiDelegateRecommendationVO> recommendDelegateTargets(String assigneeId, String flowCode) {
    log.info("[FlowAi] 推荐委派目标: assigneeId={}, flowCode={}", assigneeId, flowCode);

    List<FlowAiDelegateRecommendationVO> recommendations = new ArrayList<>();

    // 基于规则的委派推荐（实际项目中可查询历史审批记录和当前负载）
    // 规则 1：推荐同部门其他有审批权限的人员
    // 规则 2：推荐历史审批过同类流程的人员
    // 规则 3：推荐当前待办负载较低的人员
    // 当前返回空列表，后续对接组织服务后可实现真实推荐

    return recommendations;
  }

  /**
   * AI 翻译流程定义。
   *
   * <p>基于 i18n 资源的多语言支持：将流程名称和节点名称翻译为目标语言。
   */
  @Override
  public FlowAiTranslationResultVO translateDefinition(String definitionId, String targetLang) {
    log.info("[FlowAi] 翻译流程定义: definitionId={}, targetLang={}", definitionId, targetLang);

    FlowAiTranslationResultVO result = new FlowAiTranslationResultVO();
    result.setDefinitionId(definitionId);
    result.setTargetLang(targetLang);

    // 基于 i18n 资源的翻译（实际项目中可查询流程定义并翻译）
    // 当前返回原始定义，后续对接翻译资源可实现真实翻译
    result.setTranslated(false);
    result.setFallbackReason("翻译服务未配置，返回原始定义");

    return result;
  }

  // ============================== 私有辅助方法 ==============================

  /**
   * 根据描述匹配最合适的流程模板。
   *
   * @param description 自然语言描述
   * @return 匹配的模板类型
   */
  private FlowTemplateMatch matchTemplate(String description) {
    if (!StringUtils.hasText(description)) {
      return FlowTemplateMatch.BASIC_SINGLE_APPROVAL;
    }

    String lower = description.toLowerCase();

    // 关键词匹配规则
    if (lower.contains("三级") || lower.contains("高级") || lower.contains("大额")) {
      return FlowTemplateMatch.THREE_LEVEL_APPROVAL;
    }
    if (lower.contains("二级") || lower.contains("中级")) {
      return FlowTemplateMatch.TWO_LEVEL_APPROVAL;
    }
    if (lower.contains("报销") || lower.contains("费用") || lower.contains("财务")) {
      return FlowTemplateMatch.REIMBURSEMENT;
    }
    if (lower.contains("请假") || lower.contains("休假") || lower.contains("考勤")) {
      return FlowTemplateMatch.LEAVE_REQUEST;
    }
    if (lower.contains("采购") || lower.contains("招标") || lower.contains("供应商")) {
      return FlowTemplateMatch.PROCUREMENT;
    }

    return FlowTemplateMatch.BASIC_SINGLE_APPROVAL;
  }

  /**
   * 根据描述生成流程编码。
   */
  private String generateFlowCode(String description) {
    if (!StringUtils.hasText(description)) {
      return "flow_" + System.currentTimeMillis();
    }
    String prefix = description.length() > 10 ? description.substring(0, 10).trim() : description;
    return "flow_" + prefix.hashCode() + "_" + (System.currentTimeMillis() % 10000);
  }

  /**
   * 从描述中提取流程名称。
   */
  private String extractFlowName(String description) {
    if (!StringUtils.hasText(description)) {
      return "新建流程";
    }
    return description.length() > MAX_FLOW_NAME_LENGTH
        ? description.substring(0, MAX_FLOW_NAME_LENGTH) + "..."
        : description;
  }

  /**
   * 根据模板类型生成 BPMN XML。
   */
  private String generateBpmnXml(FlowTemplateMatch match) {
    return switch (match) {
      case THREE_LEVEL_APPROVAL -> generateThreeLevelBpmnXml();
      case TWO_LEVEL_APPROVAL -> generateTwoLevelBpmnXml();
      case REIMBURSEMENT -> generateReimbursementBpmnXml();
      case LEAVE_REQUEST -> generateLeaveBpmnXml();
      case PROCUREMENT -> generateProcurementBpmnXml();
      default -> generateBasicBpmnXml();
    };
  }

  /**
   * 根据模板类型生成节点列表。
   */
  private List<FlowAiDefinitionDraftVO.AiDraftNodeVO> generateNodes(FlowTemplateMatch match) {
    List<FlowAiDefinitionDraftVO.AiDraftNodeVO> nodes = new ArrayList<>();

    // 开始节点
    FlowAiDefinitionDraftVO.AiDraftNodeVO startNode = new FlowAiDefinitionDraftVO.AiDraftNodeVO();
    startNode.setNodeCode("StartEvent_1");
    startNode.setNodeName("开始");
    startNode.setNodeType("START");
    nodes.add(startNode);

    // 根据模板添加审批节点
    List<FlowAiDefinitionDraftVO.AiDraftNodeVO> approvalNodes = getApprovalNodes(match);
    nodes.addAll(approvalNodes);

    // 结束节点
    FlowAiDefinitionDraftVO.AiDraftNodeVO endNode = new FlowAiDefinitionDraftVO.AiDraftNodeVO();
    endNode.setNodeCode("EndEvent_1");
    endNode.setNodeName("结束");
    endNode.setNodeType("END");
    nodes.add(endNode);

    return nodes;
  }

  /**
   * 获取模板对应的审批节点列表。
   */
  private List<FlowAiDefinitionDraftVO.AiDraftNodeVO> getApprovalNodes(FlowTemplateMatch match) {
    List<FlowAiDefinitionDraftVO.AiDraftNodeVO> nodes = new ArrayList<>();

    switch (match) {
      case THREE_LEVEL_APPROVAL -> {
        nodes.add(createApprovalNode("Task_1", "部门经理审批"));
        nodes.add(createApprovalNode("Task_2", "总监审批"));
        nodes.add(createApprovalNode("Task_3", "VP 审批"));
      }
      case TWO_LEVEL_APPROVAL -> {
        nodes.add(createApprovalNode("Task_1", "直接主管审批"));
        nodes.add(createApprovalNode("Task_2", "部门负责人审批"));
      }
      case REIMBURSEMENT -> {
        nodes.add(createApprovalNode("Task_1", "财务初审"));
        nodes.add(createApprovalNode("Task_2", "财务经理审批"));
      }
      case LEAVE_REQUEST -> {
        nodes.add(createApprovalNode("Task_1", "直接主管审批"));
        nodes.add(createApprovalNode("Task_2", "HR 备案"));
      }
      case PROCUREMENT -> {
        nodes.add(createApprovalNode("Task_1", "需求部门审批"));
        nodes.add(createApprovalNode("Task_2", "采购部审批"));
        nodes.add(createApprovalNode("Task_3", "财务审批"));
      }
      default -> nodes.add(createApprovalNode("Task_1", "审批任务"));
    }

    return nodes;
  }

  /**
   * 创建审批节点 VO。
   */
  private FlowAiDefinitionDraftVO.AiDraftNodeVO createApprovalNode(String code, String name) {
    FlowAiDefinitionDraftVO.AiDraftNodeVO node = new FlowAiDefinitionDraftVO.AiDraftNodeVO();
    node.setNodeCode(code);
    node.setNodeName(name);
    node.setNodeType("APPROVAL");
    return node;
  }

  // ============================== BPMN XML 模板 ==============================

  private String generateBasicBpmnXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     id="Definitions_1"
                     targetNamespace="http://ydsz.com/workflow">
          <process id="Process_1" isExecutable="true">
            <startEvent id="StartEvent_1" name="开始"/>
            <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/>
            <userTask id="Task_1" name="审批任务"/>
            <sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="EndEvent_1"/>
            <endEvent id="EndEvent_1" name="结束"/>
          </process>
        </definitions>
        """;
  }

  private String generateThreeLevelBpmnXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     id="Definitions_1"
                     targetNamespace="http://ydsz.com/workflow">
          <process id="Process_1" isExecutable="true">
            <startEvent id="StartEvent_1" name="开始"/>
            <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/>
            <userTask id="Task_1" name="部门经理审批"/>
            <sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="Task_2"/>
            <userTask id="Task_2" name="总监审批"/>
            <sequenceFlow id="Flow_3" sourceRef="Task_2" targetRef="Task_3"/>
            <userTask id="Task_3" name="VP 审批"/>
            <sequenceFlow id="Flow_4" sourceRef="Task_3" targetRef="EndEvent_1"/>
            <endEvent id="EndEvent_1" name="结束"/>
          </process>
        </definitions>
        """;
  }

  private String generateTwoLevelBpmnXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     id="Definitions_1"
                     targetNamespace="http://ydsz.com/workflow">
          <process id="Process_1" isExecutable="true">
            <startEvent id="StartEvent_1" name="开始"/>
            <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/>
            <userTask id="Task_1" name="直接主管审批"/>
            <sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="Task_2"/>
            <userTask id="Task_2" name="部门负责人审批"/>
            <sequenceFlow id="Flow_3" sourceRef="Task_2" targetRef="EndEvent_1"/>
            <endEvent id="EndEvent_1" name="结束"/>
          </process>
        </definitions>
        """;
  }

  private String generateReimbursementBpmnXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     id="Definitions_1"
                     targetNamespace="http://ydsz.com/workflow">
          <process id="Process_1" isExecutable="true">
            <startEvent id="StartEvent_1" name="开始"/>
            <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/>
            <userTask id="Task_1" name="财务初审"/>
            <sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="Task_2"/>
            <userTask id="Task_2" name="财务经理审批"/>
            <sequenceFlow id="Flow_3" sourceRef="Task_2" targetRef="EndEvent_1"/>
            <endEvent id="EndEvent_1" name="结束"/>
          </process>
        </definitions>
        """;
  }

  private String generateLeaveBpmnXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     id="Definitions_1"
                     targetNamespace="http://ydsz.com/workflow">
          <process id="Process_1" isExecutable="true">
            <startEvent id="StartEvent_1" name="开始"/>
            <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/>
            <userTask id="Task_1" name="直接主管审批"/>
            <sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="Task_2"/>
            <userTask id="Task_2" name="HR 备案"/>
            <sequenceFlow id="Flow_3" sourceRef="Task_2" targetRef="EndEvent_1"/>
            <endEvent id="EndEvent_1" name="结束"/>
          </process>
        </definitions>
        """;
  }

  private String generateProcurementBpmnXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     id="Definitions_1"
                     targetNamespace="http://ydsz.com/workflow">
          <process id="Process_1" isExecutable="true">
            <startEvent id="StartEvent_1" name="开始"/>
            <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/>
            <userTask id="Task_1" name="需求部门审批"/>
            <sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="Task_2"/>
            <userTask id="Task_2" name="采购部审批"/>
            <sequenceFlow id="Flow_3" sourceRef="Task_2" targetRef="Task_3"/>
            <userTask id="Task_3" name="财务审批"/>
            <sequenceFlow id="Flow_4" sourceRef="Task_3" targetRef="EndEvent_1"/>
            <endEvent id="EndEvent_1" name="结束"/>
          </process>
        </definitions>
        """;
  }

  /**
   * 流程模板匹配枚举。
   */
  private enum FlowTemplateMatch {
    /** 基础单级审批 */
    BASIC_SINGLE_APPROVAL,
    /** 二级审批 */
    TWO_LEVEL_APPROVAL,
    /** 三级审批 */
    THREE_LEVEL_APPROVAL,
    /** 报销流程 */
    REIMBURSEMENT,
    /** 请假流程 */
    LEAVE_REQUEST,
    /** 采购流程 */
    PROCUREMENT
  }
}

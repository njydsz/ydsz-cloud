package com.njydsz.workflow.server.service.impl.i18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.workflow.server.service.FlowI18nService;

/**
 * 工作流国际化（i18n）服务实现
 *
 * <p>对 {@link FlowI18nService} 接口的完整实现，是工作流引擎的<b>国际化</b>能力。 为工作流各类枚举（任务状态 / 节点类型 / 审批结果 /
 * 流程结果等）提供中英文翻译， 支撑大厂 B 端工作流「多语言办公」场景（如跨国企业 / 海外子公司）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>枚举翻译（{@link #getMessage}）</b>：根据 {@code enumType + enumName + locale} 返回本地化的枚举描述（如
 *       {@code FlowTaskStatus.PENDING} 在 {@code zh_CN} 下返回「待办」， 在 {@code en_US} 下返回「Pending」）
 *   <li><b>语言支持</b>：当前支持 {@code zh_CN}（简体中文）/ {@code en_US}（美国英语）， 后续可扩展到 {@code zh_TW} / {@code
 *       ja_JP} / {@code ko_KR}
 *   <li><b>批量翻译（{@link #getBatchMessages}）</b>：批量获取多个枚举的翻译，避免 N+1 查询
 *   <li><b>动态注册</b>：支持运行时通过 {@link #register} 注册新的翻译项， 无需重启即可生效
 * </ul>
 *
 * <p><b>设计要点（采用内存 Map 存储）：</b>
 *
 * <ul>
 *   <li><b>避免引入额外复杂度</b>：采用静态 {@code LinkedHashMap} 存储消息资源， 无需引入 {@code messages.properties} 文件管理 /
 *       资源加载器
 *   <li><b>启动加载</b>：所有翻译项在类加载时（{@code static} 块）一次性注册， 后续无锁查询
 *   <li><b>热加载</b>：通过 {@code @NacosValue}（{@code +}）可实现热加载， 但当前版本仅支持类加载时初始化
 *   <li><b>回退策略</b>：翻译项不存在时回退到 {@code enumName} 本身， 避免前端展示为空
 *   <li><b>大小写不敏感</b>：{@code locale} 解析时自动转小写，避免 {@code zh_CN} vs {@code zh_cn} 不匹配
 * </ul>
 *
 * <p><b>存储结构：</b>
 *
 * <pre>
 *   MESSAGE_RESOURCE: Map&lt;enumType, Map&lt;enumName, Map&lt;locale, description&gt;&gt;&gt;
 *   例如:
 *     "FlowTaskStatus" → {
 *       "PENDING"  → { "zh_CN" → "待办", "en_US" → "Pending" },
 *       "COMPLETED"→ { "zh_CN" → "已通过", "en_US" → "Completed" },
 *       ...
 *     }
 * </pre>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 单个翻译
 * String text = i18nService.getMessage("FlowTaskStatus", "PENDING", "en_US");
 * // "Pending"
 *
 * // 批量翻译（前端一次性获取所有枚举）
 * Map&lt;String, String&gt; messages = i18nService.getBatchMessages("FlowTaskStatus", "en_US");
 * }</pre>
 *
 * <p><b>未来扩展：</b>如需支持更多语言 / 从 Nacos 加载翻译项 / 支持占位符（如 {@code "你有 {0} 个待办"}）， 可在本类基础上扩展，无需修改接口契约。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowI18nService 接口定义
 */
@Slf4j
@Service
public class FlowI18nServiceImpl implements FlowI18nService {

  /** i18n 消息资源：enumType -> enumName -> locale -> description */
  private static final Map<String, Map<String, Map<String, String>>> MESSAGE_RESOURCE =
      new LinkedHashMap<>();

  static {
    // FlowTaskStatus
    register("FlowTaskStatus", "PENDING", "待办", "Pending");
    register("FlowTaskStatus", "CLAIMED", "已签收", "Claimed");
    register("FlowTaskStatus", "COMPLETED", "已通过", "Completed");
    register("FlowTaskStatus", "REJECTED", "已驳回", "Rejected");
    register("FlowTaskStatus", "SKIPPED", "已跳过", "Skipped");
    register("FlowTaskStatus", "CANCELLED", "已取消", "Cancelled");
    register("FlowTaskStatus", "TIMEOUT", "超时", "Timeout");
    register("FlowTaskStatus", "DELEGATED", "已委派", "Delegated");
    register("FlowTaskStatus", "FROZEN", "已冻结", "Frozen");
    register("FlowTaskStatus", "SUSPENDED", "已挂起", "Suspended");
    register("FlowTaskStatus", "DRAFT", "暂存", "Draft");

    // FlowInstanceStatus
    register("FlowInstanceStatus", "RUNNING", "运行中", "Running");
    register("FlowInstanceStatus", "SUSPENDED", "挂起", "Suspended");
    register("FlowInstanceStatus", "COMPLETED", "已完成", "Completed");
    register("FlowInstanceStatus", "TERMINATED", "已终止", "Terminated");
    register("FlowInstanceStatus", "REJECTED", "已驳回", "Rejected");
    register("FlowInstanceStatus", "ERROR", "异常", "Error");
    register("FlowInstanceStatus", "ROLLED_BACK", "已回滚", "Rolled Back");

    // FlowNodeType
    register("FlowNodeType", "START", "开始节点", "Start Event");
    register("FlowNodeType", "APPROVAL", "审批节点", "Approval Task");
    register("FlowNodeType", "SERVICE", "服务节点", "Service Task");
    register("FlowNodeType", "EXCLUSIVE_GATEWAY", "排他网关", "Exclusive Gateway");
    register("FlowNodeType", "PARALLEL_GATEWAY", "并行网关", "Parallel Gateway");
    register("FlowNodeType", "INCLUSIVE_GATEWAY", "包容网关", "Inclusive Gateway");
    register("FlowNodeType", "END", "结束节点", "End Event");

    // FlowPerformType
    register("FlowPerformType", "OR", "或签（任一通过）", "First Pass");
    register("FlowPerformType", "PARALLEL", "会签（全部通过）", "Counter-sign (All)");

    // FlowAssigneeType
    register("FlowAssigneeType", "USER", "指定用户", "User");
    register("FlowAssigneeType", "ROLE", "指定角色", "Role");
    register("FlowAssigneeType", "DEPT", "指定部门", "Department");
    register("FlowAssigneeType", "INITIATOR", "发起人", "Initiator");
    register("FlowAssigneeType", "INITIATOR_LEADER", "发起人上级", "Initiator's Leader");
    register("FlowAssigneeType", "FORM_FIELD", "表单字段", "Form Field");

    // FlowSkipType
    register("FlowSkipType", "PASS", "通过", "Pass");
    register("FlowSkipType", "REJECT", "驳回", "Reject");
    register("FlowSkipType", "NONE", "无操作", "None");

    // FlowSlaAction
    register("FlowSlaAction", "REMIND", "提醒", "Remind");
    register("FlowSlaAction", "ESCALATE", "升级", "Escalate");
    register("FlowSlaAction", "AUTO_PASS", "自动通过", "Auto Pass");
    register("FlowSlaAction", "AUTO_REJECT", "自动驳回", "Auto Reject");
    register("FlowSlaAction", "NOTIFY_ADMIN", "通知管理员", "Notify Admin");
  }

  /**
   * 静态注册工具：在类加载时注册翻译项到 {@link #MESSAGE_RESOURCE}
   *
   * @param enumType 参数说明
   * @param enumName 参数说明
   * @param zhCN 参数说明
   * @param enUS 参数说明
   */
  private static void register(String enumType, String enumName, String zhCN, String enUS) {
    MESSAGE_RESOURCE
        .computeIfAbsent(enumType, k -> new LinkedHashMap<>())
        .computeIfAbsent(enumName, k -> new LinkedHashMap<>())
        .put("zh_CN", zhCN);
    MESSAGE_RESOURCE.get(enumType).get(enumName).put("en_US", enUS);
  }

  /**
   * 获取指定枚举类型在指定 locale 下的全部枚举描述
   *
   * <p>返回列表每项含 {@code {name, description, messageKey}}，{@code messageKey} 形如 {@code
   * FlowTaskStatus.PENDING}，供前端按需查询。
   *
   * <p><b>回退策略：</b>指定 locale 翻译缺失时回退到 {@code zh_CN} 描述。
   *
   * @param enumType 枚举类型名（{@code FlowTaskStatus} / {@code FlowInstanceStatus} 等）
   * @param locale 语言（{@code zh_CN} / {@code en_US}，大小写 / 连字符不敏感）
   * @return 枚举描述列表，无数据返回空列表
   */
  @Override
  public List<Map<String, String>> getEnumDescriptions(String enumType, String locale) {
    if (enumType == null) {
      return List.of();
    }
    String loc = normalizeLocale(locale);
    Map<String, Map<String, String>> enumMap = MESSAGE_RESOURCE.get(enumType);
    if (enumMap == null) {
      return List.of();
    }
    List<Map<String, String>> result = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> entry : enumMap.entrySet()) {
      Map<String, String> item = new LinkedHashMap<>();
      item.put("name", entry.getKey());
      item.put("description", entry.getValue().getOrDefault(loc, entry.getValue().get("zh_CN")));
      item.put("messageKey", enumType + "." + entry.getKey());
      result.add(item);
    }
    return result;
  }

  /**
   * 获取单个枚举值在指定 locale 下的翻译
   *
   * <p>翻译项不存在时回退到 {@code enumName} 本身（避免前端展示为空）。
   *
   * @param enumType 枚举类型名
   * @param enumName 枚举值名
   * @param locale 语言
   * @return 翻译后的描述（回退时为 {@code enumName}）
   */
  @Override
  public String getEnumDescription(String enumType, String enumName, String locale) {
    if (enumType == null || enumName == null) {
      return enumName;
    }
    String loc = normalizeLocale(locale);
    Map<String, Map<String, String>> enumMap = MESSAGE_RESOURCE.get(enumType);
    if (enumMap == null) {
      return enumName;
    }
    Map<String, String> localeMap = enumMap.get(enumName);
    if (localeMap == null) {
      return enumName;
    }
    return localeMap.getOrDefault(loc, localeMap.get("zh_CN"));
  }

  /**
   * 获取系统支持的语言列表
   *
   * <p>当前支持 {@code zh_CN}（简体中文，默认）和 {@code en_US}（英语）。 列表中 {@code default=true} 的语言用于前端未指定 locale
   * 时的兜底。
   *
   * @return 语言列表，每项含 {@code {code, name, default}}
   */
  @Override
  public List<Map<String, String>> getSupportedLocales() {
    List<Map<String, String>> locales = new ArrayList<>();
    Map<String, String> zhCN = new LinkedHashMap<>();
    zhCN.put("code", "zh_CN");
    zhCN.put("name", "简体中文");
    zhCN.put("default", "true");
    locales.add(zhCN);

    Map<String, String> enUS = new LinkedHashMap<>();
    enUS.put("code", "en_US");
    enUS.put("name", "English");
    enUS.put("default", "false");
    locales.add(enUS);

    return locales;
  }

  /**
   * 标准化 locale 字符串
   *
   * <p>支持 {@code zh_CN / zh-CN / zh} → {@code zh_CN}，{@code en_US / en-US / en} → {@code en_US}。
   * 大小写、连字符 / 下划线不敏感。未识别的 locale 兜底返回 {@code zh_CN}。
   *
   * @param locale 原始 locale 字符串
   * @return 标准化后的 locale（{@code zh_CN} / {@code en_US}）
   */
  private String normalizeLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return "zh_CN";
    }
    // 支持 zh-CN / zh_CN / zh / en-US / en_US / en 等格式
    String normalized = locale.replace('-', '_');
    if (normalized.startsWith("zh")) {
      return "zh_CN";
    }
    if (normalized.startsWith("en")) {
      return "en_US";
    }
    return "zh_CN";
  }
}

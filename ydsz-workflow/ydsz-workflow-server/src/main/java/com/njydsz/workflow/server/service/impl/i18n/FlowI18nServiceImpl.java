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
 * <p>对 {@link FlowI18nService} 接口的完整实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowI18nService 接口定义
 */
@Slf4j
@Service
public class FlowI18nServiceImpl implements FlowI18nService {

  /** i18n 消息资源：enumType -> enumName -> locale -> description */
  private static final Map<String, Map<String, Map<String, String>>> MESSAGE_RESOURCE =
      new LinkedHashMap<>(16);

  static {
    // 工作流任务状态
    register("FlowTaskStatus", "PENDING", "zh_CN", "待办");
    register("FlowTaskStatus", "PENDING", "en_US", "Pending");
    register("FlowTaskStatus", "COMPLETED", "zh_CN", "已通过");
    register("FlowTaskStatus", "COMPLETED", "en_US", "Completed");
    register("FlowTaskStatus", "REJECTED", "zh_CN", "已驳回");
    register("FlowTaskStatus", "REJECTED", "en_US", "Rejected");
    register("FlowTaskStatus", "CANCELED", "zh_CN", "已取消");
    register("FlowTaskStatus", "CANCELED", "en_US", "Canceled");
    register("FlowTaskStatus", "SKIPPED", "zh_CN", "已跳过");
    register("FlowTaskStatus", "SKIPPED", "en_US", "Skipped");

    // 流程实例状态
    register("FlowInstanceStatus", "RUNNING", "zh_CN", "运行中");
    register("FlowInstanceStatus", "RUNNING", "en_US", "Running");
    register("FlowInstanceStatus", "COMPLETED", "zh_CN", "已完成");
    register("FlowInstanceStatus", "COMPLETED", "en_US", "Completed");
    register("FlowInstanceStatus", "TERMINATED", "zh_CN", "已终止");
    register("FlowInstanceStatus", "TERMINATED", "en_US", "Terminated");
    register("FlowInstanceStatus", "SUSPENDED", "zh_CN", "已挂起");
    register("FlowInstanceStatus", "SUSPENDED", "en_US", "Suspended");
    register("FlowInstanceStatus", "REJECTED", "zh_CN", "已驳回");
    register("FlowInstanceStatus", "REJECTED", "en_US", "Rejected");
  }

  /**
   * 注册翻译项。
   */
  public static void register(
      String enumType, String enumName, String locale, String description) {
        MESSAGE_RESOURCE
            .computeIfAbsent(enumType, k -> new LinkedHashMap<>(16))
            .computeIfAbsent(enumName, k -> new LinkedHashMap<>(8))
            .put(locale.toLowerCase(), description);
  }

  @Override
  public List<Map<String, String>> getEnumDescriptions(String enumType, String locale) {
    if (enumType == null || locale == null) {
      return new ArrayList<>(0);
    }
    Map<String, Map<String, String>> enumMap = MESSAGE_RESOURCE.get(enumType);
    if (enumMap == null) {
      return new ArrayList<>(0);
    }
    String normalizedLocale = locale.toLowerCase();
    List<Map<String, String>> result = new ArrayList<>(enumMap.size());
    for (Map.Entry<String, Map<String, String>> entry : enumMap.entrySet()) {
      Map<String, String> item = new LinkedHashMap<>(2);
      item.put("name", entry.getKey());
      String desc = entry.getValue().get(normalizedLocale);
      item.put("description", desc != null ? desc : entry.getKey());
      result.add(item);
    }
    return result;
  }

  @Override
  public String getEnumDescription(String enumType, String enumName, String locale) {
    if (enumType == null || enumName == null) {
      return enumName;
    }
    String normalizedLocale = (locale == null ? "zh_CN" : locale).toLowerCase();
    Map<String, Map<String, String>> enumMap = MESSAGE_RESOURCE.get(enumType);
    if (enumMap == null) {
      return enumName;
    }
    Map<String, String> localeMap = enumMap.get(enumName);
    if (localeMap == null) {
      return enumName;
    }
    String desc = localeMap.get(normalizedLocale);
    return desc != null ? desc : enumName;
  }

  @Override
  public List<Map<String, String>> getSupportedLocales() {
    List<Map<String, String>> locales = new ArrayList<>(2);
    Map<String, String> zh = new LinkedHashMap<>(2);
    zh.put("code", "zh_CN");
    zh.put("name", "简体中文");
    locales.add(zh);
    Map<String, String> en = new LinkedHashMap<>(2);
    en.put("code", "en_US");
    en.put("name", "English");
    locales.add(en);
    return locales;
  }
}

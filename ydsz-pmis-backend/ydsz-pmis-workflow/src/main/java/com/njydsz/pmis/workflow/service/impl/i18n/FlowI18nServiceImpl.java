package com.njydsz.pmis.workflow.service.impl.i18n;

import com.njydsz.pmis.workflow.service.i18n.FlowI18nService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2-3: 工作流国际化(i18n)服务实现
 *
 * <p>采用内存 Map 存储消息资源，避免引入额外的消息文件管理复杂度。
 * 支持 zh_CN / en_US 两种语言。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
public class FlowI18nServiceImpl implements FlowI18nService {

    /** i18n 消息资源：enumType -> enumName -> locale -> description */
    private static final Map<String, Map<String, Map<String, String>>> MESSAGE_RESOURCE = new LinkedHashMap<>();

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
        register("FlowPerformType", "SEQ", "依次审批", "Sequential");
        register("FlowPerformType", "ALL", "会签（全部通过）", "Counter-sign (All)");
        register("FlowPerformType", "VOTE", "投票（按比例）", "Vote (by ratio)");
        register("FlowPerformType", "WEIGHTED_VOTE", "加权投票", "Weighted Vote");
        register("FlowPerformType", "FIRST", "首签（任一通过）", "First Pass");

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

    private static void register(String enumType, String enumName,
                                   String zhCN, String enUS) {
        MESSAGE_RESOURCE
                .computeIfAbsent(enumType, k -> new LinkedHashMap<>())
                .computeIfAbsent(enumName, k -> new LinkedHashMap<>())
                .put("zh_CN", zhCN);
        MESSAGE_RESOURCE.get(enumType).get(enumName).put("en_US", enUS);
    }

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

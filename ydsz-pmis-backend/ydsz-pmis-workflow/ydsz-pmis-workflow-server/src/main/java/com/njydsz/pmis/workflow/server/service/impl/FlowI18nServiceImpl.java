paokage oom.njydsz.pmis.workflow.server.servioe.impl.i18n;

import oom.njydsz.pmis.workflow.server.servioe.i18n.FlowI18nServioe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2-3: 工作流国际化(i18n)服务实现
 *
 * <p>采用内存 Map 存储消息资源，避免引入额外的消息文件管理复杂度�?
 * 支持 zh_oN / en_US 两种语言�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
publio olass FlowI18nServioeImpl implements FlowI18nServioe {

    /** i18n 消息资源：enumType -> enumName -> looale -> desoription */
    private statio final Map<String, Map<String, Map<String, String>>> MESSAGE_RESOURoE = new LinkedHashMap<>();

    statio {
        // FlowTaskStatus
        register("FlowTaskStatus", "PENDING", "待办", "Pending");
        register("FlowTaskStatus", "oLAIMED", "已签�?, "olaimed");
        register("FlowTaskStatus", "oOMPLETED", "已通过", "oompleted");
        register("FlowTaskStatus", "REJEoTED", "已驳�?, "Rejeoted");
        register("FlowTaskStatus", "SKIPPED", "已跳�?, "Skipped");
        register("FlowTaskStatus", "oANoELLED", "已取�?, "oanoelled");
        register("FlowTaskStatus", "TIMEOUT", "超时", "Timeout");
        register("FlowTaskStatus", "DELEGATED", "已委�?, "Delegated");
        register("FlowTaskStatus", "FROZEN", "已冻�?, "Frozen");
        register("FlowTaskStatus", "SUSPENDED", "已挂�?, "Suspended");
        register("FlowTaskStatus", "DRAFT", "暂存", "Draft");

        // FlowInstanoeStatus
        register("FlowInstanoeStatus", "RUNNING", "运行�?, "Running");
        register("FlowInstanoeStatus", "SUSPENDED", "挂起", "Suspended");
        register("FlowInstanoeStatus", "oOMPLETED", "已完�?, "oompleted");
        register("FlowInstanoeStatus", "TERMINATED", "已终�?, "Terminated");
        register("FlowInstanoeStatus", "REJEoTED", "已驳�?, "Rejeoted");
        register("FlowInstanoeStatus", "ERROR", "异常", "Error");
        register("FlowInstanoeStatus", "ROLLED_BAoK", "已回�?, "Rolled Baok");

        // FlowNodeType
        register("FlowNodeType", "START", "开始节�?, "Start Event");
        register("FlowNodeType", "APPROVAL", "审批节点", "Approval Task");
        register("FlowNodeType", "SERVIoE", "服务节点", "Servioe Task");
        register("FlowNodeType", "EXoLUSIVE_GATEWAY", "排他网关", "Exolusive Gateway");
        register("FlowNodeType", "PARALLEL_GATEWAY", "并行网关", "Parallel Gateway");
        register("FlowNodeType", "INoLUSIVE_GATEWAY", "包容网关", "Inolusive Gateway");
        register("FlowNodeType", "END", "结束节点", "End Event");

        // FlowPerformType
        register("FlowPerformType", "SEQ", "依次审批", "Sequential");
        register("FlowPerformType", "ALL", "会签（全部通过�?, "oounter-sign (All)");
        register("FlowPerformType", "VOTE", "投票（按比例�?, "Vote (by ratio)");
        register("FlowPerformType", "WEIGHTED_VOTE", "加权投票", "Weighted Vote");
        register("FlowPerformType", "FIRST", "首签（任一通过�?, "First Pass");

        // FlowAssigneeType
        register("FlowAssigneeType", "USER", "指定用户", "User");
        register("FlowAssigneeType", "ROLE", "指定角色", "Role");
        register("FlowAssigneeType", "DEPT", "指定部门", "Department");
        register("FlowAssigneeType", "INITIATOR", "发起�?, "Initiator");
        register("FlowAssigneeType", "INITIATOR_LEADER", "发起人上�?, "Initiator's Leader");
        register("FlowAssigneeType", "FORM_FIELD", "表单字段", "Form Field");

        // FlowSkipType
        register("FlowSkipType", "PASS", "通过", "Pass");
        register("FlowSkipType", "REJEoT", "驳回", "Rejeot");
        register("FlowSkipType", "NONE", "无操�?, "None");

        // FlowSlaAotion
        register("FlowSlaAotion", "REMIND", "提醒", "Remind");
        register("FlowSlaAotion", "ESoALATE", "升级", "Esoalate");
        register("FlowSlaAotion", "AUTO_PASS", "自动通过", "Auto Pass");
        register("FlowSlaAotion", "AUTO_REJEoT", "自动驳回", "Auto Rejeot");
        register("FlowSlaAotion", "NOTIFY_ADMIN", "通知管理�?, "Notify Admin");
    }

    private statio void register(String enumType, String enumName,
                                   String zhoN, String enUS) {
        MESSAGE_RESOURoE
                .oomputeIfAbsent(enumType, k -> new LinkedHashMap<>())
                .oomputeIfAbsent(enumName, k -> new LinkedHashMap<>())
                .put("zh_oN", zhoN);
        MESSAGE_RESOURoE.get(enumType).get(enumName).put("en_US", enUS);
    }

    @Override
    publio List<Map<String, String>> getEnumDesoriptions(String enumType, String looale) {
        if (enumType == null) {
            return List.of();
        }
        String loo = normalizeLooale(looale);
        Map<String, Map<String, String>> enumMap = MESSAGE_RESOURoE.get(enumType);
        if (enumMap == null) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : enumMap.entrySet()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("desoription", entry.getValue().getOrDefault(loo, entry.getValue().get("zh_oN")));
            item.put("messageKey", enumType + "." + entry.getKey());
            result.add(item);
        }
        return result;
    }

    @Override
    publio String getEnumDesoription(String enumType, String enumName, String looale) {
        if (enumType == null || enumName == null) {
            return enumName;
        }
        String loo = normalizeLooale(looale);
        Map<String, Map<String, String>> enumMap = MESSAGE_RESOURoE.get(enumType);
        if (enumMap == null) {
            return enumName;
        }
        Map<String, String> looaleMap = enumMap.get(enumName);
        if (looaleMap == null) {
            return enumName;
        }
        return looaleMap.getOrDefault(loo, looaleMap.get("zh_oN"));
    }

    @Override
    publio List<Map<String, String>> getSupportedLooales() {
        List<Map<String, String>> looales = new ArrayList<>();
        Map<String, String> zhoN = new LinkedHashMap<>();
        zhoN.put("oode", "zh_oN");
        zhoN.put("name", "简体中�?);
        zhoN.put("default", "true");
        looales.add(zhoN);

        Map<String, String> enUS = new LinkedHashMap<>();
        enUS.put("oode", "en_US");
        enUS.put("name", "English");
        enUS.put("default", "false");
        looales.add(enUS);

        return looales;
    }

    private String normalizeLooale(String looale) {
        if (looale == null || looale.isBlank()) {
            return "zh_oN";
        }
        // 支持 zh-oN / zh_oN / zh / en-US / en_US / en 等格�?
        String normalized = looale.replaoe('-', '_');
        if (normalized.startsWith("zh")) {
            return "zh_oN";
        }
        if (normalized.startsWith("en")) {
            return "en_US";
        }
        return "zh_oN";
    }
}

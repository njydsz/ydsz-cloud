package com.njydsz.pmis.workflow.server.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.service.FlowCustomButtonService;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点自定义按钮服务实现（P2-4）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCustomButtonServiceImpl implements FlowCustomButtonService {

    /** 流程节点 Mapper，用于读取和更新节点 ext 配置 */
    private final FlowNodeMapper nodeMapper;
    /** 运行时任务 Mapper，用于查询按钮执行关联的待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程定义缓存服务，按钮配置变更后主动失效缓存 */
    private final FlowDefinitionCacheService definitionCacheService;
    /** 流程任务服务，按钮动作（通过/驳回/转办/委派）的执行入口 */
    private final FlowTaskService taskService;

    @Override
    public List<Map<String, Object>> getCustomButtons(String definitionId, String nodeCode) {
        FlowNodeDO node = definitionCacheService.getNodeByCode(definitionId, nodeCode);
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return List.of();
        }
        return parseCustomButtons(node.getExt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCustomButtons(String definitionId, String nodeCode, List<Map<String, Object>> buttons) {
        FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_node_not_found", nodeCode);
        }
        // 读取现有 ext JSON
        JSONObject extJson = StringUtils.hasText(node.getExt())
                ? JSON.parseObject(node.getExt()) : new JSONObject();
        // 写入 customButtons
        if (buttons == null || buttons.isEmpty()) {
            extJson.remove("customButtons");
        } else {
            extJson.put("customButtons", buttons);
        }
        node.setExt(extJson.toJSONString());
        nodeMapper.updateById(node);
        // 失效缓存
        definitionCacheService.evict(definitionId);
        log.info("[CustomButton] 保存节点自定义按钮: definitionId={} nodeCode={} count={}",
                definitionId, nodeCode, buttons == null ? 0 : buttons.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeButton(String taskId, String buttonCode,
                                              String userId, String comment,
                                              Map<String, Object> variables) {
        FlowRunTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_6541ab08", taskId);
        }

        // 获取节点自定义按钮
        List<Map<String, Object>> buttons = getCustomButtons(task.getDefinitionId(), task.getNodeCode());
        Map<String, Object> button = buttons.stream()
                .filter(b -> buttonCode.equals(String.valueOf(b.get("code"))))
                .findFirst()
                .orElseThrow(() -> new SysException(StandardResultCode.BAD_REQUEST,
                        "error.workflow.msg_button_not_found", buttonCode));

        String action = String.valueOf(button.getOrDefault("action", "CUSTOM")).toUpperCase();
        String targetNodeCode = button.get("targetNodeCode") != null
                ? String.valueOf(button.get("targetNodeCode")) : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("buttonCode", buttonCode);
        result.put("action", action);

        switch (action) {
            case "PASS" -> {
                FlowTaskOperateDTO passDto = new FlowTaskOperateDTO();
                passDto.setTaskId(taskId);
                passDto.setUserId(userId);
                passDto.setComment(comment);
                passDto.setVariables(variables);
                taskService.pass(passDto);
                result.put("result", "PASSED");
            }
            case "REJECT" -> {
                FlowTaskOperateDTO rejectDto = new FlowTaskOperateDTO();
                rejectDto.setTaskId(taskId);
                rejectDto.setUserId(userId);
                rejectDto.setComment(comment);
                rejectDto.setTargetNodeCode(targetNodeCode);
                rejectDto.setVariables(variables);
                taskService.reject(rejectDto);
                result.put("result", "REJECTED");
                result.put("targetNodeCode", targetNodeCode);
            }
            case "TRANSFER" -> {
                String targetUserId = variables != null ? String.valueOf(variables.get("targetUserId")) : null;
                String targetUserName = variables != null ? String.valueOf(variables.get("targetUserName")) : null;
                if (StringUtils.hasText(targetUserId)) {
                    FlowTaskOperateDTO transferDto = new FlowTaskOperateDTO();
                    transferDto.setTaskId(taskId);
                    transferDto.setUserId(userId);
                    transferDto.setComment(comment);
                    transferDto.setTargetUserId(targetUserId);
                    transferDto.setTargetUserName(targetUserName);
                    taskService.transfer(transferDto);
                    result.put("result", "TRANSFERRED");
                } else {
                    throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_transfer_target_required");
                }
            }
            case "DELEGATE" -> {
                String delegateUserId = variables != null ? String.valueOf(variables.get("targetUserId")) : null;
                String delegateUserName = variables != null ? String.valueOf(variables.get("targetUserName")) : null;
                if (StringUtils.hasText(delegateUserId)) {
                    FlowTaskOperateDTO delegateDto = new FlowTaskOperateDTO();
                    delegateDto.setTaskId(taskId);
                    delegateDto.setUserId(userId);
                    delegateDto.setTargetUserId(delegateUserId);
                    delegateDto.setTargetUserName(delegateUserName);
                    taskService.delegate(delegateDto);
                    result.put("result", "DELEGATED");
                } else {
                    throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_delegate_target_required");
                }
            }
            case "CUSTOM" -> {
                // 自定义回调：由前端或事件监听器处理
                result.put("result", "CUSTOM");
                result.put("callbackUrl", button.get("callbackUrl"));
                log.info("[CustomButton] 自定义按钮操作: taskId={} buttonCode={} callbackUrl={}",
                        taskId, buttonCode, button.get("callbackUrl"));
            }
            default -> throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_unknown_button_action", action);
        }

        log.info("[CustomButton] 执行按钮操作: taskId={} buttonCode={} action={} userId={}",
                taskId, buttonCode, action, userId);
        return result;
    }

    // ============================== 内部辅助 ==============================

    /**
     * 从节点 ext JSON 中解析 customButtons
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCustomButtons(String extJson) {
        if (!StringUtils.hasText(extJson)) {
            return List.of();
        }
        try {
            JSONObject ext = JSON.parseObject(extJson);
            Object buttons = ext.get("customButtons");
            if (buttons == null) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            if (buttons instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add((Map<String, Object>) map);
                    }
                }
            }
            result.sort(Comparator.comparingInt(b ->
                    b.get("sortNum") == null ? 0 : ((Number) b.get("sortNum")).intValue()));
            return result;
        } catch (Exception e) {
            log.warn("[CustomButton] 解析 customButtons 失败: {} err={}", extJson, e.getMessage());
            return List.of();
        }
    }
}

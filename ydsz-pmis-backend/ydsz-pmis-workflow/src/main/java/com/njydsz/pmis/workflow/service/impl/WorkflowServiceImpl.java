package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.DeployProcessDTO;
import com.njydsz.pmis.workflow.dto.StartProcessDTO;
import com.njydsz.pmis.workflow.dto.TaskOperateDTO;
import com.njydsz.pmis.workflow.entity.WorkflowBusinessDO;
import com.njydsz.pmis.workflow.flow.WorkflowFacade;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.mapper.WorkflowBusinessMapper;
import com.njydsz.pmis.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流核心服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final WorkflowBusinessMapper businessMapper;

    // ==================== 流程定义 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deploy(DeployProcessDTO dto) {
        if (!StringUtils.hasText(dto.getBpmnXml())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "BPMN XML 不能为空");
        }
        try {
            // 解析 XML 提取流程名（可选）
            String name = dto.getName();
            if (!StringUtils.hasText(name)) {
                try (java.io.ByteArrayInputStream bin =
                             new java.io.ByteArrayInputStream(dto.getBpmnXml().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(
                            new org.flowable.common.engine.api.io.InputStreamProvider() {
                                @Override
                                public java.io.InputStream getInputStream() {
                                    return new java.io.ByteArrayInputStream(
                                            dto.getBpmnXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                }
                            },
                            false,
                            false
                    );
                    if (model != null && model.getMainProcess() != null) {
                        name = model.getMainProcess().getName();
                    }
                }
            }
            String category = dto.getCategory() == null ? "default" : dto.getCategory();
            Deployment deployment = repositoryService.createDeployment()
                    .name(name == null ? "process" : name)
                    .category(category)
                    .tenantId(String.valueOf(dto.getTenantId() == null ? 1 : dto.getTenantId()))
                    .addString((name == null ? "process" : name) + ".bpmn20.xml", dto.getBpmnXml())
                    .deploy();
            ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();
            log.info("[Workflow] 部署流程成功: name={} id={} key={}",
                    name, def.getId(), def.getKey());
            return def.getId();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Workflow] 部署流程失败: {}", e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "部署失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deployFromClasspath(String name, String classpathResource) {
        try (InputStream in = new ClassPathResource(classpathResource).getInputStream()) {
            Deployment deployment = repositoryService.createDeployment()
                    .name(name)
                    .addInputStream(classpathResource, in)
                    .deploy();
            ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();
            return def.getId();
        } catch (Exception e) {
            log.error("[Workflow] 从 classpath 部署失败: {}", e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "部署失败: " + e.getMessage());
        }
    }

    @Override
    public Page<ProcessDefinition> pageDefinitions(int page, int size, String category, String key) {
        Page<ProcessDefinition> result = new Page<>(page, size);
        ProcessDefinitionQuery q = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc();
        if (StringUtils.hasText(category)) {
            q.processDefinitionCategory(category);
        }
        if (StringUtils.hasText(key)) {
            q.processDefinitionKeyLike("%" + key + "%");
        }
        long total = q.count();
        List<ProcessDefinition> list = q.listPage((page - 1) * size, size);
        result.setTotal(total);
        result.setRecords(list);
        return result;
    }

    @Override
    public ProcessDefinition getLatestDefinition(String processKey) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
    }

    @Override
    public void suspendDefinition(String processDefinitionId) {
        repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
        log.info("[Workflow] 挂起流程定义: {}", processDefinitionId);
    }

    @Override
    public void activateDefinition(String processDefinitionId) {
        repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
        log.info("[Workflow] 激活流程定义: {}", processDefinitionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(String deploymentId, boolean cascade) {
        repositoryService.deleteDeployment(deploymentId, cascade);
        log.info("[Workflow] 删除流程部署: {} cascade={}", deploymentId, cascade);
    }

    @Override
    public InputStream readDefinitionXml(String processDefinitionId) {
        return repositoryService.getResourceAsStream(
                getDeploymentId(processDefinitionId),
                getDefinitionResourceName(processDefinitionId)
        );
    }

    // ==================== 流程实例 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startProcess(StartProcessDTO dto) {
        // ========== 双轨开关：按 flowCode 路由到 LocalWorkflowFacade 或 Flowable ==========
        if (shouldUseLocal(dto.getProcessKey())) {
            log.info("[Workflow] 双轨路由: flowCode={} → local 引擎", dto.getProcessKey());
            FlowStartProcessDTO localDto = new FlowStartProcessDTO();
            localDto.setFlowCode(dto.getProcessKey());
            localDto.setBusinessType(dto.getBusinessType());
            localDto.setBusinessId(dto.getBusinessId());
            localDto.setBusinessNo(dto.getBusinessNo());
            localDto.setTitle(dto.getTitle());
            localDto.setInitiatorId(dto.getInitiatorId());
            localDto.setInitiatorName(dto.getInitiatorName());
            localDto.setVariables(dto.getVariables());
            return localWorkflowFacade.startProcess(localDto);
        }

        try {
            Map<String, Object> vars = dto.getVariables() == null ? new HashMap<>() : dto.getVariables();
            // 注入业务变量
            vars.put("businessType", dto.getBusinessType());
            vars.put("businessId", dto.getBusinessId());
            vars.put("businessNo", dto.getBusinessNo());
            vars.put("initiatorId", dto.getInitiatorId());
            vars.put("initiatorName", dto.getInitiatorName());

            ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                    dto.getProcessKey(),
                    dto.getBusinessId(),
                    vars
            );

            // 记录业务关联
            WorkflowBusinessDO biz = new WorkflowBusinessDO();
            biz.setProcessInstanceId(pi.getId());
            biz.setProcessDefinitionKey(pi.getProcessDefinitionKey());
            biz.setProcessDefinitionId(pi.getProcessDefinitionId());
            biz.setBusinessType(dto.getBusinessType());
            biz.setBusinessId(dto.getBusinessId());
            biz.setBusinessNo(dto.getBusinessNo());
            biz.setTitle(dto.getTitle());
            biz.setInitiatorId(dto.getInitiatorId());
            biz.setInitiatorName(dto.getInitiatorName());
            biz.setStatus("RUNNING");
            biz.setStartTime(LocalDateTime.now());
            biz.setTenantId(1L);
            businessMapper.insert(biz);

            log.info("[Workflow] 启动流程: key={} piId={} bizType={} bizId={}",
                    dto.getProcessKey(), pi.getId(), dto.getBusinessType(), dto.getBusinessId());
            return pi.getId();
        } catch (FlowableException e) {
            log.error("[Workflow] 启动流程失败: {}", e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "启动流程失败: " + e.getMessage());
        }
    }

    /** 双轨路由判断：local 模式 + 命中白名单 → 走自建 */
    private boolean shouldUseLocal(String flowCode) {
        if (!"local".equalsIgnoreCase(flowEngine)) {
            return false;
        }
        if (!StringUtils.hasText(flowCode) || localFlowCodes == null) {
            return false;
        }
        return localFlowCodes.contains(flowCode);
    }

    @Override
    public void suspendInstance(String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
        businessMapper.updateStatusByInstanceId(processInstanceId, "SUSPENDED", null, null, null);
        log.info("[Workflow] 挂起流程实例: {}", processInstanceId);
    }

    @Override
    public void activateInstance(String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
        businessMapper.updateStatusByInstanceId(processInstanceId, "RUNNING", null, null, null);
        log.info("[Workflow] 激活流程实例: {}", processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateInstance(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
        // 通过历史服务拿耗时
        HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        Long durationMs = null;
        LocalDateTime endTime = LocalDateTime.now();
        if (hi != null && hi.getEndTime() != null && hi.getStartTime() != null) {
            durationMs = hi.getDurationInMillis();
            endTime = LocalDateTime.ofInstant(hi.getEndTime().toInstant(), ZoneId.systemDefault());
        }
        businessMapper.updateStatusByInstanceId(processInstanceId, "TERMINATED", null, endTime, durationMs);
        log.info("[Workflow] 终止流程实例: {} reason={}", processInstanceId, reason);
    }

    @Override
    public Map<String, Object> getInstanceVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    // ==================== 任务 ====================

    @Override
    public List<Map<String, Object>> listTodoTasks(Long userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        TaskQuery q = taskService.createTaskQuery().taskCandidateOrAssigned(String.valueOf(userId));
        long total = q.count();
        if (total == 0) {
            return List.of();
        }
        List<Task> tasks = q.orderByTaskCreateTime().desc()
                .listPage((page - 1) * size, size);
        return tasks.stream().map(this::taskToMap).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listDoneTasks(Long userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        List<org.flowable.task.api.history.HistoricTaskInstance> list = historyService
                .createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .listPage((page - 1) * size, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (org.flowable.task.api.history.HistoricTaskInstance t : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", t.getId());
            m.put("name", t.getName());
            m.put("processInstanceId", t.getProcessInstanceId());
            m.put("assignee", t.getAssignee());
            m.put("startTime", t.getStartTime());
            m.put("endTime", t.getEndTime());
            m.put("durationInMillis", t.getDurationInMillis());
            // 反查 processDefinitionKey
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(t.getProcessInstanceId())
                    .singleResult();
            m.put("processDefinitionKey", pi == null ? null : pi.getProcessDefinitionKey());
            result.add(m);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(TaskOperateDTO dto) {
        if (!StringUtils.hasText(dto.getTaskId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务 ID 不能为空");
        }
        if (StringUtils.hasText(dto.getComment())) {
            taskService.addComment(dto.getTaskId(), getProcessInstanceId(dto.getTaskId()), dto.getComment());
        }
        if (dto.getVariables() != null && !dto.getVariables().isEmpty()) {
            taskService.setVariables(dto.getTaskId(), dto.getVariables());
        }
        taskService.complete(dto.getTaskId());
        log.info("[Workflow] 完成任务: taskId={} action={} userId={}",
                dto.getTaskId(), dto.getAction(), dto.getUserId());
    }

    @Override
    public void claimTask(String taskId, Long userId) {
        if (userId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "签收人不能为空");
        }
        taskService.claim(taskId, String.valueOf(userId));
        log.info("[Workflow] 签收任务: taskId={} userId={}", taskId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(TaskOperateDTO dto) {
        if (!StringUtils.hasText(dto.getTaskId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务 ID 不能为空");
        }
        if (StringUtils.hasText(dto.getComment())) {
            taskService.addComment(dto.getTaskId(), getProcessInstanceId(dto.getTaskId()), dto.getComment());
        }
        String piId = getProcessInstanceId(dto.getTaskId());
        String currentActivityId = getCurrentActivityId(dto.getTaskId());
        if (StringUtils.hasText(dto.getTargetNodeKey()) && StringUtils.hasText(currentActivityId)) {
            try {
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(piId)
                        .moveActivityIdTo(currentActivityId, dto.getTargetNodeKey())
                        .changeState();
            } catch (Exception e) {
                log.warn("[Workflow] 跳转到目标节点失败，改用终止流程: {}", e.getMessage());
                runtimeService.deleteProcessInstance(piId, "REJECTED: " + dto.getComment());
                businessMapper.updateStatusByInstanceId(piId, "TERMINATED", null,
                        LocalDateTime.now(), null);
            }
        } else {
            // 终止流程
            runtimeService.deleteProcessInstance(piId, "REJECTED: " + dto.getComment());
            businessMapper.updateStatusByInstanceId(piId, "TERMINATED", null,
                    LocalDateTime.now(), null);
        }
        log.info("[Workflow] 退回任务: taskId={} target={}", dto.getTaskId(), dto.getTargetNodeKey());
    }

    @Override
    public void delegateTask(TaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "委派人不能为空");
        }
        taskService.delegateTask(dto.getTaskId(), String.valueOf(dto.getTargetUserId()));
        log.info("[Workflow] 委派任务: taskId={} → userId={}", dto.getTaskId(), dto.getTargetUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(TaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "转办目标人不能为空");
        }
        // 转办：将任务所有权交给目标人（设置 assignee）
        taskService.setAssignee(dto.getTaskId(), String.valueOf(dto.getTargetUserId()));
        log.info("[Workflow] 转办任务: taskId={} → userId={}", dto.getTaskId(), dto.getTargetUserId());
    }

    // ==================== 业务关联 ====================

    @Override
    public WorkflowBusinessDO getByBusiness(String businessType, String businessId) {
        return businessMapper.selectByBusiness(businessType, businessId);
    }

    @Override
    public WorkflowBusinessDO getByProcessInstance(String processInstanceId) {
        return businessMapper.selectByProcessInstanceId(processInstanceId);
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> taskToMap(Task t) {
        Map<String, Object> m = new HashMap<>();
        m.put("taskId", t.getId());
        m.put("name", t.getName());
        m.put("processInstanceId", t.getProcessInstanceId());
        m.put("processDefinitionId", t.getProcessDefinitionId());
        m.put("assignee", t.getAssignee());
        m.put("owner", t.getOwner());
        m.put("createTime", t.getCreateTime());
        m.put("dueDate", t.getDueDate());
        m.put("description", t.getDescription());
        m.put("category", t.getCategory());
        // 反查 processDefinitionKey
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(t.getProcessInstanceId())
                    .singleResult();
            m.put("processDefinitionKey", pi == null ? null : pi.getProcessDefinitionKey());
        } catch (Exception ignored) {
            m.put("processDefinitionKey", null);
        }
        return m;
    }

    private String getDeploymentId(String processDefinitionId) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId).singleResult();
        return def == null ? null : def.getDeploymentId();
    }

    private String getDefinitionResourceName(String processDefinitionId) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId).singleResult();
        return def == null ? null : def.getResourceName();
    }

    private String getProcessInstanceId(String taskId) {
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        return t == null ? null : t.getProcessInstanceId();
    }

    private String getCurrentActivityId(String taskId) {
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        return t == null ? null : t.getTaskDefinitionKey();
    }
}

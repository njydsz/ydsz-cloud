package com.njydsz.pmis.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.workflow.dto.DeployProcessDTO;
import com.njydsz.pmis.workflow.dto.StartProcessDTO;
import com.njydsz.pmis.workflow.dto.TaskOperateDTO;
import com.njydsz.pmis.workflow.entity.WorkflowBusinessDO;
import org.flowable.engine.repository.ProcessDefinition;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 工作流核心服务
 *
 * <p>统一封装流程定义、实例、任务、业务的常用操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface WorkflowService {

    // ==================== 流程定义 ====================

    /**
     * 部署流程（基于 BPMN XML 字符串）
     *
     * @return 流程定义 ID
     */
    String deploy(DeployProcessDTO dto);

    /**
     * 部署流程（基于 classpath 资源）
     */
    String deployFromClasspath(String name, String classpathResource);

    /**
     * 分页查询流程定义
     */
    Page<ProcessDefinition> pageDefinitions(int page, int size, String category, String key);

    /**
     * 获取最新版本流程定义
     */
    ProcessDefinition getLatestDefinition(String processKey);

    /**
     * 挂起流程定义
     */
    void suspendDefinition(String processDefinitionId);

    /**
     * 激活流程定义
     */
    void activateDefinition(String processDefinitionId);

    /**
     * 删除流程定义
     */
    void deleteDefinition(String deploymentId, boolean cascade);

    /**
     * 读取流程定义 BPMN XML
     */
    InputStream readDefinitionXml(String processDefinitionId);

    // ==================== 流程实例 ====================

    /**
     * 启动流程实例
     *
     * @return 流程实例 ID
     */
    String startProcess(StartProcessDTO dto);

    /**
     * 挂起流程实例
     */
    void suspendInstance(String processInstanceId);

    /**
     * 激活流程实例
     */
    void activateInstance(String processInstanceId);

    /**
     * 终止流程实例
     */
    void terminateInstance(String processInstanceId, String reason);

    /**
     * 查询流程实例变量
     */
    Map<String, Object> getInstanceVariables(String processInstanceId);

    // ==================== 任务 ====================

    /**
     * 查询用户的待办任务
     */
    List<Map<String, Object>> listTodoTasks(Long userId, int page, int size);

    /**
     * 查询用户的已办任务
     */
    List<Map<String, Object>> listDoneTasks(Long userId, int page, int size);

    /**
     * 完成任务（审批通过）
     */
    void completeTask(TaskOperateDTO dto);

    /**
     * 签收任务
     */
    void claimTask(String taskId, Long userId);

    /**
     * 退回任务
     */
    void rejectTask(TaskOperateDTO dto);

    /**
     * 委派任务
     */
    void delegateTask(TaskOperateDTO dto);

    /**
     * 转办任务
     */
    void transferTask(TaskOperateDTO dto);

    // ==================== 业务关联 ====================

    /**
     * 反查业务单据关联的流程
     */
    WorkflowBusinessDO getByBusiness(String businessType, String businessId);

    /**
     * 反查流程实例关联的业务
     */
    WorkflowBusinessDO getByProcessInstance(String processInstanceId);
}

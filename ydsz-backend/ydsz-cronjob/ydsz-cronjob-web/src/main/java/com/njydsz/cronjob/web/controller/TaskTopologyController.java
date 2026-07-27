package com.njydsz.cronjob.web.controller.dag;

import java.util.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-11: 任务执行拓扑图后端 API。
 *
 * <p>提供任务执行全链路拓扑数据，支持前端可视化展示：
 * <ul>
 *   <li>DAG 工作流节点/边定义 + 每个节点的实时执行状态</li>
 *   <li>任务执行日志关联（每个节点关联最近一次执行日志）</li>
 *   <li>执行时间线（开始/结束/耗时）</li>
 * </ul>
 *
 * <h3>返回数据结构</h3>
 * <pre>{@code
 * {
 *   "dagDefinition": { "nodes": [...], "edges": [...] },
 *   "dagInstance": { "id": "...", "status": "RUNNING", ... },
 *   "nodeInstances": [
 *     { "jobKey": "a", "status": "SUCCESS", "startTime": "...", "endTime": "...", "durationMs": 1234, "logId": "..." },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务执行拓扑图")
@RestController
@RequestMapping("/cronjob/topology")
@RequiredArgsConstructor
public class TaskTopologyController {

    /** DAG 实例 Mapper */
    private final JobDagInstanceMapper dagInstanceMapper;
    /** DAG 节点实例 Mapper */
    private final JobDagNodeInstanceMapper dagNodeInstanceMapper;
    /** DAG 定义 Mapper */
    private final JobDagMapper dagMapper;
    /** DAG 定义 JSON 编解码器 */
    private final DagDefinitionCodec dagDefinitionCodec;
    /** 任务执行日志 Mapper */
    private final JobLogMapper jobLogMapper;

    /**
     * 查询 DAG 实例的执行拓扑图数据。
     *
     * @param dagInstanceId DAG 实例 ID
     * @return 拓扑图数据（DAG 定义 + 实例状态 + 节点执行详情）
     */
    @Operation(summary = "查询DAG实例执行拓扑图")
    @GetMapping("/dagInstance/{dagInstanceId}")
    public BaseResponse<Map<String, Object>> getDagInstanceTopology(@PathVariable String dagInstanceId) {
        JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null) {
            return BaseResponse.success(null);
        }

        // 加载 DAG 定义
        JobDag dag = dagMapper.selectById(instance.getDagId());
        DagDefinition definition = dag != null
                ? dagDefinitionCodec.fromJson(dag.getDagDefinition())
                : DagDefinition.empty();

        // 查询节点实例
        List<JobDagNodeInstance> nodeInstances = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);

        // 构建拓扑数据
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("dagDefinition", definition);
        topology.put("dagInstance", instance);
        topology.put("nodeInstances", nodeInstances);

        return BaseResponse.success(topology);
    }

    /**
     * 查询任务的执行历史拓扑（最近 N 次执行）。
     *
     * @param jobKey 任务 KEY
     * @return 执行历史列表
     */
    @Operation(summary = "查询任务执行历史")
    @GetMapping("/jobHistory/{jobKey}")
    public BaseResponse<List<JobLog>> getJobExecutionHistory(@PathVariable String jobKey) {
        LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobLog::getJobKey, jobKey)
                .eq(JobLog::getDeleted, 0)
                .orderByDesc(JobLog::getCreatedAt)
                .last("LIMIT 20");
        return BaseResponse.success(jobLogMapper.selectList(wrapper));
    }
}

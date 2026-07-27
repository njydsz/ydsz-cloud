package com.njydsz.common.core.job;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * MapReduce 任务执行上下文
 *
 * <p>在 {@link MapProcessor} / {@link MapReduceProcessor} 执行过程中传递状态与数据，
 * 支持 Map 阶段（子任务调度）与 Reduce 阶段（结果汇总）的完整生命周期。
 *
 * <p><b>上下文组成：</b>
 * <ul>
 *   <li><b>任务标识</b>：{@link #jobId} / {@link #logId} / {@link #jobKey} / {@link #taskName} 共同唯一定位一次执行</li>
 *   <li><b>任务参数</b>：{@link #taskParams}（JSON 字符串）业务方按需解析</li>
 *   <li><b>拓扑结构</b>：{@link #root} 标识是否为根任务，{@link #subTasks} 仅 Root 任务可写</li>
 *   <li><b>结果存储</b>：{@link #results} 在 Reduce 阶段用于跨节点汇总（如分片计数、聚合指标）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>Root 任务：负责任务拆分，构造子任务列表（{@link #addSubTask}）</li>
 *   <li>子任务：消费 Root 派发的分片数据，结果写入 {@link #results}</li>
 *   <li>Reduce 阶段：汇总所有 {@link #results}，生成最终统计</li>
 * </ul>
 *
 * <p><b>线程安全：</b>非线程安全，禁止跨线程共享同一实例；MapReduce 框架为每个分片构造独立上下文。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MapProcessor
 * @see MapReduceProcessor
 * @see MapTask
 */
@Data
public class MapContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID，调度框架生成，全局唯一 */
    private String jobId;

    /** 执行日志 ID，对应 ydsz_job_log 表的主键 */
    private String logId;

    /** 任务 KEY，业务方注册时指定的标识（如 "orderSyncJob"） */
    private String jobKey;

    /** 当前执行的任务名称（子任务时为子任务名） */
    private String taskName;

    /** 任务参数 JSON 字符串，业务方按需反序列化 */
    private String taskParams;

    /** 是否为 Root 任务；Root 任务可向 {@link #subTasks} 派发子任务 */
    private boolean root;

    /** 子任务列表（仅 root 任务可写） */
    private List<MapTask> subTasks = new ArrayList<>();

    /** 任务结果存储（reduce 阶段使用） */
    private Map<String, Object> results = new HashMap<>();

    public MapContext() {
    }

    public MapContext(String jobId, String logId, String jobKey, String taskName,
                      String taskParams, boolean root) {
        this.jobId = jobId;
        this.logId = logId;
        this.jobKey = jobKey;
        this.taskName = taskName;
        this.taskParams = taskParams;
        this.root = root;
    }

    /**
     * 判断是否为 Root 任务
     *
     * @return true-Root 任务（可派发子任务）；false-子任务（消费分片数据）
     */
    public boolean isRoot() {
        return root;
    }

    /**
     * 添加子任务
     *
     * <p>便捷方法，内部构造 {@link MapTask}。仅 Root 任务可调用。
     *
     * @param taskName   子任务名称
     * @param taskParams 子任务参数 JSON
     */
    public void addSubTask(String taskName, String taskParams) {
        this.subTasks.add(new MapTask(taskName, taskParams));
    }

    /**
     * 添加子任务
     *
     * @param subTask 子任务对象
     */
    public void addSubTask(MapTask subTask) {
        this.subTasks.add(subTask);
    }
}

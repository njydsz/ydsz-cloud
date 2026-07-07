package com.njydsz.pmis.cronjob.core.dag;

import java.util.Objects;

/**
 * DAG 节点定义（P2 DAG 增强）。
 *
 * <p>对应 dag_definition JSON 中的 nodes 数组元素，描述一个任务节点
 * 及其在前端可视化画布上的坐标位置。
 *
 * @param jobKey     任务 KEY（唯一标识节点，边通过 jobKey 引用）
 * @param jobId      任务 ID（冗余，便于直接派发）
 * @param label      节点显示名称（前端画布展示）
 * @param x          画布 X 坐标（前端可视化用）
 * @param y          画布 Y 坐标（前端可视化用）
 * @param paramsJson 节点级参数 JSON（覆盖任务默认 paramsJson，null 表示用任务默认值）
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public record DagNode(String jobKey, String jobId, String label,
                       int x, int y, String paramsJson) {

    /**
     * 紧凑构造器：校验 jobKey 非空。
     */
    public DagNode {
        Objects.requireNonNull(jobKey, "jobKey 不能为空");
    }

    /**
     * 工厂方法：创建节点（坐标默认 0,0）。
     */
    public static DagNode of(String jobKey, String jobId, String label) {
        return new DagNode(jobKey, jobId, label, 0, 0, null);
    }

    /**
     * 工厂方法：创建带坐标的节点。
     */
    public static DagNode of(String jobKey, String jobId, String label, int x, int y) {
        return new DagNode(jobKey, jobId, label, x, y, null);
    }
}

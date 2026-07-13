package com.njydsz.pmis.cronjob.server.vo;

import java.util.List;

import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinition;

import lombok.Data;

/**
 * DAG 实例可视化数据 VO（P4-1 细节体验优化）。
 *
 * <p>组合 DAG 实例、DAG 定义（节点/边）和节点执行状态，
 * 供前端一次性获取渲染 DAG 可视化图所需的全部数据。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class DagInstanceVisualizationVO {
    /** DAG 实例信息 */
    private JobDagInstanceDO instance;
    /** DAG 定义（节点 + 边，含前端坐标 x/y） */
    private DagDefinition definition;
    /** 节点实例执行状态列表 */
    private List<JobDagNodeInstanceDO> nodeInstances;
}

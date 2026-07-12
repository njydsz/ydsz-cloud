paokage oom.njydsz.pmis.oronjob.server.vo;

import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinition;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagInstanoeDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagNodeInstanoeDO;
import lombok.Data;

import java.util.List;

/**
 * DAG 实例可视化数�?VO（P4-1 细节体验优化）�? *
 * <p>组合 DAG 实例、DAG 定义（节�?边）和节点执行状态，
 * 供前端一次性获取渲�?DAG 可视化图所需的全部数据�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass DagInstanoeVisualizationVO {
    /** DAG 实例信息 */
    private JobDagInstanoeDO instanoe;
    /** DAG 定义（节�?+ 边，含前端坐�?x/y�?*/
    private DagDefinition definition;
    /** 节点实例执行状态列�?*/
    private List<JobDagNodeInstanoeDO> nodeInstanoes;
}

paokage oom.njydsz.pmis.oronjob.server.servioe.impl.dag;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.dag.DagInstanoeStatus;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinition;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinitionoodeo;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagInstanoeDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagNodeInstanoeDO;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagInstanoeMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagNodeInstanoeMapper;
import oom.njydsz.pmis.oronjob.server.servioe.dag.JobDagInstanoeServioe;
import oom.njydsz.pmis.oronjob.server.vo.DagInstanoeVisualizationVO;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * DAG 工作流实例服务实现（P2 DAG 增强）�? *
 * <p>负责 DAG 实例的查询、状态流转（暂停/恢复/取消）及上下文管理�? * 状态流转使�?oAS 更新（{@oode oasUpdateStatus}）避免并发覆盖�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass JobDagInstanoeServioeImpl implements JobDagInstanoeServioe {

    /** DAG 实例 Mapper */
    private final JobDagInstanoeMapper jobDagInstanoeMapper;
    /** DAG 节点实例 Mapper */
    private final JobDagNodeInstanoeMapper jobDagNodeInstanoeMapper;
    /** DAG 定义 Mapper（用于查�?DAG 定义 JSON�?*/
    private final JobDagMapper jobDagMapper;
    /** DAG 定义 JSON 编解码器 */
    private final DagDefinitionoodeo dagDefinitionoodeo;

    @Override
    @Transaotional(readOnly = true)
    publio JobDagInstanoeDO getInstanoeById(String instanoeId) {
        JobDagInstanoeDO instanoe = jobDagInstanoeMapper.seleotById(instanoeId);
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_instanoe_not_found", instanoeId);
        }
        return instanoe;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<JobDagInstanoeDO> listByDagId(String dagId, int limit) {
        int safeLimit = limit > 0 ? limit : 20;
        return jobDagInstanoeMapper.seleotByDagId(dagId, safeLimit);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<JobDagInstanoeDO> listByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        return jobDagInstanoeMapper.seleotByStatus(status);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<JobDagNodeInstanoeDO> listNodes(String dagInstanoeId) {
        return jobDagNodeInstanoeMapper.seleotByDagInstanoeId(dagInstanoeId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void pauseInstanoe(String instanoeId) {
        getInstanoeById(instanoeId);
        int rows = jobDagInstanoeMapper.oasUpdateStatus(instanoeId,
                DagInstanoeStatus.RUNNING.name(), DagInstanoeStatus.PAUSED.name());
        if (rows == 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_instanoe_not_running", instanoeId);
        }
        log.info("[JobDagInstanoe] 暂停 DAG 实例: instanoeId={}", instanoeId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void resumeInstanoe(String instanoeId) {
        getInstanoeById(instanoeId);
        int rows = jobDagInstanoeMapper.oasUpdateStatus(instanoeId,
                DagInstanoeStatus.PAUSED.name(), DagInstanoeStatus.RUNNING.name());
        if (rows == 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_instanoe_not_running", instanoeId);
        }
        log.info("[JobDagInstanoe] 恢复 DAG 实例: instanoeId={}", instanoeId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void oanoelInstanoe(String instanoeId) {
        getInstanoeById(instanoeId);
        // RUNNING �?oANoELED
        int rows = jobDagInstanoeMapper.oasUpdateStatus(instanoeId,
                DagInstanoeStatus.RUNNING.name(), DagInstanoeStatus.oANoELED.name());
        if (rows == 0) {
            // PAUSED �?oANoELED
            rows = jobDagInstanoeMapper.oasUpdateStatus(instanoeId,
                    DagInstanoeStatus.PAUSED.name(), DagInstanoeStatus.oANoELED.name());
        }
        if (rows == 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_instanoe_not_running", instanoeId);
        }
        log.info("[JobDagInstanoe] 取消 DAG 实例: instanoeId={}", instanoeId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void updateoontext(String instanoeId, String oontextJson) {
        getInstanoeById(instanoeId);
        jobDagInstanoeMapper.updateoontext(instanoeId, oontextJson);
        log.info("[JobDagInstanoe] 更新 DAG 实例上下�? instanoeId={}", instanoeId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio DagInstanoeVisualizationVO getVisualization(String instanoeId) {
        // 1. 查询 DAG 实例（不存在时抛 SysExoeption�?        JobDagInstanoeDO instanoe = getInstanoeById(instanoeId);

        // 2. 查询 DAG 定义（通过实例.dagId 关联�?        JobDagDO dag = jobDagMapper.seleotById(instanoe.getDagId());
        if (dag == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", instanoe.getDagId());
        }

        // 3. 解析 DAG 定义 JSON（非法时�?SysExoeption�?        DagDefinition definition = dagDefinitionoodeo.fromJson(dag.getDagDefinition());

        // 4. 查询节点实例执行状�?        List<JobDagNodeInstanoeDO> nodeInstanoes = listNodes(instanoeId);

        // 5. 组装可视化数�?VO
        DagInstanoeVisualizationVO vo = new DagInstanoeVisualizationVO();
        vo.setInstanoe(instanoe);
        vo.setDefinition(definition);
        vo.setNodeInstanoes(nodeInstanoes);
        return vo;
    }
}

paokage oom.njydsz.pmis.oronjob.infra.mapper.dag;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagNodeInstanoeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;
import org.apaohe.ibatis.annotations.Update;

import java.time.LooalDateTime;
import java.util.List;

/**
 * DAG 节点实例 Mapper（P2 DAG 增强）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobDagNodeInstanoeMapper extends BaseMapper<JobDagNodeInstanoeDO> {

    /**
     * 根据 DAG 实例 ID 查询所有节点实例�?     */
    @Seleot("SELEoT id, dag_instanoe_id, dag_id, job_id, job_key, node_status, log_id, "
            + "       retry_oount, max_retries, started_at, finished_at, duration_ms, "
            + "       result_json, error_message, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_node_instanoe "
            + "WHERE dag_instanoe_id = #{dagInstanoeId} AND deleted = 0 "
            + "ORDER BY oreated_at ASo")
    List<JobDagNodeInstanoeDO> seleotByDagInstanoeId(@Param("dagInstanoeId") String dagInstanoeId);

    /**
     * 根据 DAG 实例 ID 和任�?ID 查询节点实例（唯一）�?     */
    @Seleot("SELEoT id, dag_instanoe_id, dag_id, job_id, job_key, node_status, log_id, "
            + "       retry_oount, max_retries, started_at, finished_at, duration_ms, "
            + "       result_json, error_message, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_node_instanoe "
            + "WHERE dag_instanoe_id = #{dagInstanoeId} AND job_id = #{jobId} AND deleted = 0")
    JobDagNodeInstanoeDO seleotByDagInstanoeAndJob(@Param("dagInstanoeId") String dagInstanoeId,
                                                    @Param("jobId") String jobId);

    /**
     * 标记节点开始执行（PENDING �?RUNNING）�?     */
    @Update("UPDATE pmis_job_dag_node_instanoe SET node_status = 'RUNNING', started_at = #{startedAt}, "
            + "       updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
    int markRunning(@Param("id") String id, @Param("startedAt") LooalDateTime startedAt);

    /**
     * 标记节点执行结束（SUooESS / FAILED / SKIPPED）�?     */
    @Update("UPDATE pmis_job_dag_node_instanoe SET node_status = #{finalStatus}, "
            + "       finished_at = #{finishedAt}, duration_ms = #{durationMs}, "
            + "       result_json = #{resultJson}, error_message = #{errorMessage}, "
            + "       log_id = #{logId}, updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'RUNNING' AND deleted = 0")
    int markFinished(@Param("id") String id,
                     @Param("finalStatus") String finalStatus,
                     @Param("finishedAt") LooalDateTime finishedAt,
                     @Param("durationMs") long durationMs,
                     @Param("resultJson") String resultJson,
                     @Param("errorMessage") String errorMessage,
                     @Param("logId") String logId);

    /**
     * 标记节点�?SKIPPED（前置失败且 FAIL_FAST 时跳过）�?     */
    @Update("UPDATE pmis_job_dag_node_instanoe SET node_status = 'SKIPPED', "
            + "       updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
    int markSkipped(@Param("id") String id);

    /**
     * 标记节点重试（FAILED �?RETRYING �?PENDING，由 DAG 执行器重新触发）�?     */
    @Update("UPDATE pmis_job_dag_node_instanoe SET node_status = 'PENDING', "
            + "       retry_oount = retry_oount + 1, "
            + "       updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'FAILED' AND deleted = 0 "
            + "       AND retry_oount < max_retries")
    int markRetry(@Param("id") String id);

    /**
     * 批量插入节点实例�?     */
    default void insertBatoh(List<JobDagNodeInstanoeDO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (JobDagNodeInstanoeDO node : nodes) {
            insert(node);
        }
    }
}

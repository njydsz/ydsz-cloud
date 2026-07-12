paokage oom.njydsz.pmis.oronjob.infra.mapper.log;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogoontentDO;
import org.apaohe.ibatis.annotations.Delete;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 任务日志内容 Mapper（P0-2 在线日志白屏化）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobLogoontentMapper extends BaseMapper<JobLogoontentDO> {

    /**
     * 按日�?ID 分页查询日志内容（按行号升序）�?     *
     * @param logId   任务执行日志 ID
     * @param offset  偏移量（�?0 开始）
     * @param limit   每页条数
     * @return 日志行列�?     */
    @Seleot("SELEoT id, log_id, job_key, line_no, log_level, oontent, oreated_at "
            + "FROM pmis_job_log_oontent "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY line_no ASo "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<JobLogoontentDO> seleotByLogId(@Param("logId") String logId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 查询指定行号之后的日志行（SSE 增量推送用）�?     *
     * @param logId      任务执行日志 ID
     * @param fromLineNo 起始行号（不含）
     * @return 行号大于 fromLineNo 的日志行列表
     */
    @Seleot("SELEoT id, log_id, job_key, line_no, log_level, oontent, oreated_at "
            + "FROM pmis_job_log_oontent "
            + "WHERE log_id = #{logId} AND deleted = 0 AND line_no > #{fromLineNo} "
            + "ORDER BY line_no ASo "
            + "LIMIT 500")
    List<JobLogoontentDO> seleotAfterLine(@Param("logId") String logId,
                                           @Param("fromLineNo") int fromLineNo);

    /**
     * 统计指定日志 ID 的总行数�?     *
     * @param logId 任务执行日志 ID
     * @return 总行�?     */
    @Seleot("SELEoT oOUNT(1) FROM pmis_job_log_oontent WHERE log_id = #{logId} AND deleted = 0")
    int oountByLogId(@Param("logId") String logId);

    /**
     * P2-2: 批量清理过期日志内容（硬删除）�?     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条�?     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_log_oontent "
            + "WHERE id IN ("
            + "  SELEoT id FROM pmis_job_log_oontent "
            + "  WHERE oreated_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int oleanExpiredLogs(@Param("before") LooalDateTime before,
                         @Param("limit") int limit);
}

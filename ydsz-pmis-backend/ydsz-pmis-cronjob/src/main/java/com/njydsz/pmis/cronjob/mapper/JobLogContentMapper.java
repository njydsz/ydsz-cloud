package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobLogContentDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务日志内容 Mapper（P0-2 在线日志白屏化）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobLogContentMapper extends BaseMapper<JobLogContentDO> {

    /**
     * 按日志 ID 分页查询日志内容（按行号升序）。
     *
     * @param logId   任务执行日志 ID
     * @param offset  偏移量（从 0 开始）
     * @param limit   每页条数
     * @return 日志行列表
     */
    @Select("SELECT id, log_id, job_key, line_no, log_level, content, created_at "
            + "FROM pmis_job_log_content "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY line_no ASC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<JobLogContentDO> selectByLogId(@Param("logId") String logId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 查询指定行号之后的日志行（SSE 增量推送用）。
     *
     * @param logId      任务执行日志 ID
     * @param fromLineNo 起始行号（不含）
     * @return 行号大于 fromLineNo 的日志行列表
     */
    @Select("SELECT id, log_id, job_key, line_no, log_level, content, created_at "
            + "FROM pmis_job_log_content "
            + "WHERE log_id = #{logId} AND deleted = 0 AND line_no > #{fromLineNo} "
            + "ORDER BY line_no ASC "
            + "LIMIT 500")
    List<JobLogContentDO> selectAfterLine(@Param("logId") String logId,
                                           @Param("fromLineNo") int fromLineNo);

    /**
     * 统计指定日志 ID 的总行数。
     *
     * @param logId 任务执行日志 ID
     * @return 总行数
     */
    @Select("SELECT COUNT(1) FROM pmis_job_log_content WHERE log_id = #{logId} AND deleted = 0")
    int countByLogId(@Param("logId") String logId);

    /**
     * P2-2: 批量清理过期日志内容（硬删除）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_log_content "
            + "WHERE id IN ("
            + "  SELECT id FROM pmis_job_log_content "
            + "  WHERE created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}

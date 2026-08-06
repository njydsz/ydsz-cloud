package com.remisoft.cronjob.infra.mapper.log;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.cronjob.domain.entity.log.JobLogContent;

/**
 * 任务日志大字段 Mapper
 *
 * <p>对应数据表 <code>remi_job_log_content</code>。
 * <p>与 {@code remi_job_log} 1:1 拆分，避免主表膨胀影响列表查询性能。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_log_id — 日志 ID 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.cronjob.domain.entity.log.JobLogContent 日志内容实体
 * @see com.remisoft.cronjob.server.service.JobLogService 日志 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobLogContentMapper extends BaseMapper<JobLogContent> {

    /**
     * 按日志 ID 分页查询日志内容（按行号升序）。
     *
     * @param logId   任务执行日志 ID
     * @param offset  偏移量（从 0 开始）
     * @param limit   每页条数
     * @return 日志行列表
     */
    @Select("SELECT id, log_id, job_key, line_no, log_level, content, created_at "
            + "FROM remi_job_log_content "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY line_no ASC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<JobLogContent> selectByLogId(@Param("logId") String logId,
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
            + "FROM remi_job_log_content "
            + "WHERE log_id = #{logId} AND deleted = 0 AND line_no > #{fromLineNo} "
            + "ORDER BY line_no ASC "
            + "LIMIT 500")
    List<JobLogContent> selectAfterLine(@Param("logId") String logId,
                                           @Param("fromLineNo") int fromLineNo);

    /**
     * 统计指定日志 ID 的总行数。
     *
     * @param logId 任务执行日志 ID
     * @return 总行数
     */
    @Select("SELECT COUNT(1) FROM remi_job_log_content WHERE log_id = #{logId} AND deleted = 0")
    int countByLogId(@Param("logId") String logId);

    /**
     * P1-9: 关键字搜索日志内容（大小写不敏感）。
     *
     * @param logId   任务执行日志 ID
     * @param keyword 搜索关键词
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 匹配的日志行列表
     */
    @Select("SELECT id, log_id, job_key, line_no, log_level, content, created_at "
            + "FROM remi_job_log_content "
            + "WHERE log_id = #{logId} AND deleted = 0 AND content ILIKE '%' || #{keyword} || '%' "
            + "ORDER BY line_no ASC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<JobLogContent> selectByLogIdAndKeyword(@Param("logId") String logId,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /**
     * P2-2: 批量清理过期日志内容（硬删除）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM remi_job_log_content "
            + "WHERE id IN ("
            + "  SELECT id FROM remi_job_log_content "
            + "  WHERE created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}

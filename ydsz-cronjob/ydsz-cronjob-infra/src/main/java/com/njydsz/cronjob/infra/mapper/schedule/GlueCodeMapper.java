package com.njydsz.cronjob.infra.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.schedule.GlueCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * GLUE 脚本代码 Mapper
 *
 * <p>对应数据表 <code>ydsz_glue_code</code>。
 *
 * <p>GLUE 模式允许任务以脚本方式实现（Shell/Python/SQL/JS），脚本内容存于本表，与 Job 解耦。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_job_glue — (任务+GLUE 类型) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.entity.schedule.GlueCode GLUE 实体
 * @see com.njydsz.cronjob.server.service.GlueCodeService GLUE Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface GlueCodeMapper extends BaseMapper<GlueCode> {

  /**
   * 查询指定任务的最新版本 GLUE 代码。
   *
   * <p>按 version 降序取第一条未删除记录。
   *
   * @param jobId 任务 ID
   * @return 最新版本 GLUE 代码；不存在时返回 null
   */
  @Select(
      "SELECT id, job_id, source_code, language, version, remark, "
          + "       created_by, created_at, deleted "
          + "FROM ydsz_job_glue "
          + "WHERE job_id = #{jobId} AND deleted = 0 "
          + "ORDER BY version DESC "
          + "LIMIT 1")
  GlueCode selectLatestByJobId(@Param("jobId") String jobId);
}

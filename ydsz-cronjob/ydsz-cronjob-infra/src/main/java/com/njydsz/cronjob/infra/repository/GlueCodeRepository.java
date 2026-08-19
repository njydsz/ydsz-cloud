package com.njydsz.cronjob.infra.repository;

import com.njydsz.cronjob.infra.entity.schedule.GlueCode;

/**
 * GLUE 脚本 Repository。
 *
 * <p>封装 {@code ydsz_glue_code} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface GlueCodeRepository {

  /**
   * 根据任务 ID 查询最新版本的 GLUE 代码。
   *
   * @param jobId 任务 ID
   * @return 最新版本的 GLUE 代码记录，不存在时返回 null
   */
  GlueCode selectLatestByJobId(String jobId);
}

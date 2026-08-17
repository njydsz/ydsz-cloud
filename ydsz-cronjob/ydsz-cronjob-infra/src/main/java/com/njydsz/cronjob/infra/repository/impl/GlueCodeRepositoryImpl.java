package com.njydsz.cronjob.infra.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.schedule.GlueCode;
import com.njydsz.cronjob.infra.mapper.schedule.GlueCodeMapper;
import com.njydsz.cronjob.infra.repository.GlueCodeRepository;

/**
 * GLUE 脚本 Repository 实现。
 *
 * <p>委托 {@link GlueCodeMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class GlueCodeRepositoryImpl implements GlueCodeRepository {

  private final GlueCodeMapper glueCodeMapper;

  @Override
  public GlueCode selectLatestByJobId(String jobId) {
    return glueCodeMapper.selectLatestByJobId(jobId);
  }
}

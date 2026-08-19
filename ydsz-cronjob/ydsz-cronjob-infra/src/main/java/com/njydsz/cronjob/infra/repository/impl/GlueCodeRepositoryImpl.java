package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.GlueCodeRepository;
import com.njydsz.cronjob.domain.vo.GlueCodeVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.schedule.GlueCode;
import com.njydsz.cronjob.infra.mapper.schedule.GlueCodeMapper;

/**
 * GLUE 脚本 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class GlueCodeRepositoryImpl implements GlueCodeRepository {

  private final GlueCodeMapper glueCodeMapper;
  private final CronjobConverter converter;

  @Override
  public Optional<GlueCodeVO> findLatestByJobId(String jobId) {
    return Optional.ofNullable(glueCodeMapper.selectLatestByJobId(jobId))
        .map(converter::entityToVO);
  }

  @Override
  public List<GlueCodeVO> findAllByJobId(String jobId) {
    return converter.glueCodeListToVO(glueCodeMapper.selectAllByJobId(jobId));
  }

  @Override
  public String insert(GlueCodeVO vo) {
    GlueCode entity = converter.voToEntity(vo);
    glueCodeMapper.insert(entity);
    return entity.getId();
  }
}

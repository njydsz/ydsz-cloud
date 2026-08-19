package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.cronjob.infra.entity.schedule.GlueCode;
import com.njydsz.cronjob.domain.repository.GlueCodeRepository;
import com.njydsz.cronjob.domain.vo.GlueCodeVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.schedule.GlueCodeMapper;

/**
 * GLUE 脚本 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link GlueCodeRepository} 接口，封装 GlueCodeMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
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

  // ===== 实体方法实现 =====

  @Override
  public GlueCode selectLatestByJobId(String jobId) {
    return glueCodeMapper.selectLatestByJobId(jobId);
  }

  @Override
  public List<GlueCode> selectList(LambdaQueryWrapper<GlueCode> wrapper) {
    return glueCodeMapper.selectList(wrapper);
  }

  @Override
  public GlueCode selectOne(LambdaQueryWrapper<GlueCode> wrapper) {
    return glueCodeMapper.selectOne(wrapper);
  }

  @Override
  public int insert(GlueCode entity) {
    return glueCodeMapper.insert(entity);
  }
}

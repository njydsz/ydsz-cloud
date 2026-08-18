package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.LanguageCreateDTO;
import com.njydsz.userinfo.domain.dto.LanguageUpdateDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.repository.LanguageRepository;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.entity.LanguageDO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;

/**
 * 语言配置 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link LanguageMapper} 实现语言配置的数据访问。
 * 所有返回值通过 {@link UserInfoConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class LanguageRepositoryImpl implements LanguageRepository {

  private final LanguageMapper languageMapper;
  private final UserInfoConverter converter;

  @Override
  public Optional<LanguageVO> findById(String id) {
    LanguageDO entity = languageMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<LanguageVO> findByLanguageCode(String languageCode) {
    LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(LanguageDO::getLanguageCode, languageCode);
    LanguageDO entity = languageMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<LanguageVO> findDefault() {
    LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(LanguageDO::getIsDefault, 1);
    LanguageDO entity = languageMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<LanguageVO>> page(LanguagePageQuery query) {
    Page<LanguageDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<LanguageDO> wrapper = buildWrapper(query);
    Page<LanguageDO> result = languageMapper.selectPage(page, wrapper);
    List<LanguageVO> vos = converter.languageListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<LanguageVO> list(LanguagePageQuery query) {
    LambdaQueryWrapper<LanguageDO> wrapper = buildWrapper(query);
    List<LanguageDO> entities = languageMapper.selectList(wrapper);
    return converter.languageListToVO(entities);
  }

  @Override
  public LanguageVO create(LanguageCreateDTO dto) {
    LanguageDO entity = converter.createDtoToEntity(dto);
    languageMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public LanguageVO update(LanguageUpdateDTO dto) {
    LanguageDO entity = converter.updateDtoToEntity(dto);
    languageMapper.updateById(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public boolean deleteById(String id) {
    return languageMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(LanguagePageQuery query) {
    LambdaQueryWrapper<LanguageDO> wrapper = buildWrapper(query);
    return languageMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<LanguageDO> buildWrapper(LanguagePageQuery query) {
    LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getLanguageCode() != null && !query.getLanguageCode().isBlank()) {
      wrapper.like(LanguageDO::getLanguageCode, query.getLanguageCode());
    }
    if (query.getLanguageName() != null && !query.getLanguageName().isBlank()) {
      wrapper.like(LanguageDO::getLanguageName, query.getLanguageName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(LanguageDO::getStatus, query.getStatus());
    }
    return wrapper;
  }
}

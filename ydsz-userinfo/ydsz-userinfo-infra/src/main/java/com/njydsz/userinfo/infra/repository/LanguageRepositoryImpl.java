package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.LanguageDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.repository.LanguageRepository;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.infra.converter.UserInfoAuthConverter;
import com.njydsz.userinfo.infra.entity.Language;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;

/**
 * 语言配置 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link LanguageMapper} 实现语言配置的数据访问。
 * 所有返回值通过 {@link UserInfoAuthConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class LanguageRepositoryImpl implements LanguageRepository {

  private final LanguageMapper languageMapper;
  private final UserInfoAuthConverter converter;

  @Override
  public Optional<LanguageVO> findById(String id) {
    Language entity = languageMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<LanguageVO> findByLanguageCode(String languageCode) {
    LambdaQueryWrapper<Language> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Language::getLanguageCode, languageCode);
    Language entity = languageMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<LanguageVO> findDefault() {
    LambdaQueryWrapper<Language> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Language::getIsDefault, 1);
    Language entity = languageMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<LanguageVO>> page(LanguagePageQuery query) {
    Page<Language> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Language> wrapper = buildWrapper(query);
    Page<Language> result = languageMapper.selectPage(page, wrapper);
    List<LanguageVO> vos = converter.languageListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<LanguageVO> list(LanguagePageQuery query) {
    LambdaQueryWrapper<Language> wrapper = buildWrapper(query);
    List<Language> entities = languageMapper.selectList(wrapper);
    return converter.languageListToVO(entities);
  }

  @Override
  public LanguageVO save(LanguageDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      Language entity = converter.dtoToEntity(dto);
      languageMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      Language entity = converter.dtoToEntityWithId(dto);
      languageMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return languageMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(LanguagePageQuery query) {
    LambdaQueryWrapper<Language> wrapper = buildWrapper(query);
    return languageMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<Language> buildWrapper(LanguagePageQuery query) {
    LambdaQueryWrapper<Language> wrapper = new LambdaQueryWrapper<>();
    if (query.getLanguageCode() != null && !query.getLanguageCode().isBlank()) {
      wrapper.like(Language::getLanguageCode, query.getLanguageCode());
    }
    if (query.getLanguageName() != null && !query.getLanguageName().isBlank()) {
      wrapper.like(Language::getLanguageName, query.getLanguageName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Language::getStatus, query.getStatus());
    }
    return wrapper;
  }
}

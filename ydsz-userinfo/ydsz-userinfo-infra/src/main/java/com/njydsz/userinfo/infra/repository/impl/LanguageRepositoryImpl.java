package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.Language;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;
import com.njydsz.userinfo.infra.repository.LanguageRepository;

/**
 * 语言配置 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link LanguageMapper} 实现语言配置的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class LanguageRepositoryImpl implements LanguageRepository {

  private final LanguageMapper languageMapper;

  @Override
  public Language findById(String id) {
    return languageMapper.selectById(id);
  }

  @Override
  public Language findByLanguageCode(String languageCode) {
    LambdaQueryWrapper<Language> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Language::getLanguageCode, languageCode);
    return languageMapper.selectOne(wrapper);
  }

  @Override
  public Language findDefault() {
    LambdaQueryWrapper<Language> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Language::getIsDefault, true);
    return languageMapper.selectOne(wrapper);
  }

  @Override
  public IPage<Language> page(Page<Language> page, QueryWrapper<Language> wrapper) {
    return languageMapper.selectPage(page, wrapper);
  }

  @Override
  public List<Language> list(LambdaQueryWrapper<Language> wrapper) {
    return languageMapper.selectList(wrapper);
  }

  @Override
  public int insert(Language entity) {
    return languageMapper.insert(entity);
  }

  @Override
  public int updateById(Language entity) {
    return languageMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return languageMapper.deleteById(id);
  }

  @Override
  public long count(LambdaQueryWrapper<Language> wrapper) {
    return languageMapper.selectCount(wrapper);
  }
}

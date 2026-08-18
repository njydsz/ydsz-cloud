package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.LanguageRepository;
import com.njydsz.userinfo.infra.entity.LanguageDO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;

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
  public LanguageDO findById(String id) {
    return languageMapper.selectById(id);
  }

  @Override
  public LanguageDO findByLanguageCode(String languageCode) {
    LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(LanguageDO::getLanguageCode, languageCode);
    return languageMapper.selectOne(wrapper);
  }

  @Override
  public LanguageDO findDefault() {
    LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(LanguageDO::getIsDefault, true);
    return languageMapper.selectOne(wrapper);
  }

  @Override
  public IPage<LanguageDO> page(Page<LanguageDO> page, QueryWrapper<LanguageDO> wrapper) {
    return languageMapper.selectPage(page, wrapper);
  }

  @Override
  public List<LanguageDO> list(LambdaQueryWrapper<LanguageDO> wrapper) {
    return languageMapper.selectList(wrapper);
  }

  @Override
  public int insert(LanguageDO entity) {
    return languageMapper.insert(entity);
  }

  @Override
  public int updateById(LanguageDO entity) {
    return languageMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return languageMapper.deleteById(id);
  }

  @Override
  public long count(LambdaQueryWrapper<LanguageDO> wrapper) {
    return languageMapper.selectCount(wrapper);
  }
}

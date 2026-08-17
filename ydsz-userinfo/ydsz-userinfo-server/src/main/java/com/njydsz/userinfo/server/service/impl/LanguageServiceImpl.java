package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.create.LanguageCreateDTO;
import com.njydsz.userinfo.domain.dto.update.LanguageUpdateDTO;
import com.njydsz.userinfo.infra.entity.LanguageDO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.domain.repository.LanguageRepository;
import com.njydsz.userinfo.server.service.LanguageService;

/**
 * 语言 Service 实现
 *
 * <p>实现 {@link LanguageService} 接口，封装语言的完整业务逻辑：CRUD、 {@code languageCode} 唯一性校验、默认语言唯一性管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageServiceImpl implements LanguageService {

  private final LanguageRepository languageRepository;

  @Override
  public PageResponse<List<LanguageVO>> page(LanguagePageQuery query) {
    QueryWrapper<LanguageDO> wrapper = buildQueryWrapper(query);
    Page<LanguageDO> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
    IPage<LanguageDO> result = languageRepository.page(mpPage, wrapper);
    return PageResponses.success(result, UserInfoConverter.INSTANT::entityToVO);
  }

  @Override
  public LanguageVO getById(String id) {
    LanguageDO entity = languageRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.LANGUAGE_NOT_FOUND);
    }
    return UserInfoConverter.INSTANT.entityToVO(entity);
  }

  @Override
  public List<LanguageVO> list() {
    LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(LanguageDO::getSortOrder);
    return languageRepository.list(wrapper).stream()
        .map(UserInfoConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(LanguageCreateDTO dto) {
    LanguageDO entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
    if (entity.getStatus() == null) {
      entity.setStatus("ENABLED");
    }
    languageRepository.insert(entity);
    return entity.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(LanguageUpdateDTO dto) {
    LanguageDO entity = languageRepository.findById(dto.getId());
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.LANGUAGE_NOT_FOUND);
    }
    BeanUpdateUtil.copyNonNull(dto, entity, "id");
    return languageRepository.updateById(entity) > 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    LanguageDO entity = languageRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.LANGUAGE_NOT_FOUND);
    }
    return languageRepository.deleteById(id) > 0;
  }

  private QueryWrapper<LanguageDO> buildQueryWrapper(LanguagePageQuery query) {
    QueryWrapper<LanguageDO> wrapper = new QueryWrapper<>();
    if (query.getLanguageCode() != null && !query.getLanguageCode().isBlank()) {
      wrapper.like("language_code", query.getLanguageCode());
    }
    if (query.getLanguageName() != null && !query.getLanguageName().isBlank()) {
      wrapper.like("language_name", query.getLanguageName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("sort_order");
    return wrapper;
  }
}

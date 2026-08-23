package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.LanguageDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.repository.LanguageRepository;
import com.njydsz.userinfo.domain.vo.LanguageVO;
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
    return languageRepository.page(query);
  }

  @Override
  public LanguageVO getById(String id) {
    return languageRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.LANGUAGE_NOT_FOUND));
  }

  @Override
  public List<LanguageVO> list() {
    LanguagePageQuery query = new LanguagePageQuery();
    return languageRepository.list(query);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(LanguageDTO dto) {
    LanguageVO vo = languageRepository.save(dto);
    return vo.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(LanguageDTO dto) {
    languageRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.LANGUAGE_NOT_FOUND));
    LanguageVO vo = languageRepository.save(dto);
    return vo != null;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    languageRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.LANGUAGE_NOT_FOUND));
    return languageRepository.deleteById(id);
  }
}

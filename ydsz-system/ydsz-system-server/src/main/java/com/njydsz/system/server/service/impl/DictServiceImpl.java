package com.njydsz.system.server.service.impl;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.spring.YdszCacheable;
import com.njydsz.system.server.constant.SystemCacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;
import com.njydsz.system.server.service.event.DictChangeEventConstants;
import com.njydsz.system.server.service.event.DictChangeEventPublisher;


/**
 * \u5b57\u5178\u7c7b\u578b Service \u5b9e\u73b0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

  /** \u5b57\u5178\u4ed3\u50a8 */
  private final DictRepository dictRepository;

  /** \u7edf\u4e00\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u95e8\u9762 */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** Redis \u5b57\u5178\u53d8\u66f4\u4e8b\u4ef6\u53d1\u5e03\u5668\uff08\u7528\u4e8e SSE \u591a\u7aef\u5e7f\u64ad\uff09 */
  private final DictChangeEventPublisher dictChangeEventPublisher;

  // ============================== CRUD ==============================

  /** \u5206\u9875\u67e5\u8be2 */
  @Override
  public PageResponse<List<DictTypeVO>> page(DictPageQuery query) {
    return dictRepository.findTypePage(query);
  }

  /** \u6839\u636e\u4e3b\u952e\u67e5\u8be2 */
  @Override
  public DictTypeVO getById(String id) {
    return dictRepository.findTypeById(id).orElse(null);
  }

  /** \u65b0\u589e\u5b57\u5178\u7c7b\u578b */
  @Override
  @CacheEvict(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "T(com.njyzsz.common.cache.support.CacheKeyBuilder).build('system','dict:type','all')")
  @Transactional(rollbackFor = Exception.class)
  public String save(DictTypeDTO dto) {
    checkDuplicateTypeCode(dto);
    dictRepository.insertType(dto);
    publishDictTypeChangedEvent(dto.getTypeCode(), "\u521b\u5efa\u5b57\u5178\u7c7b\u578b");
    dictChangeEventPublisher.publishDictTypeEvent(
        dto.getTypeCode(), DictChangeEventConstants.EVENT_TYPE_CREATED);
    return dto.getId();
  }

  /** \u66f4\u65b0\u5b57\u5178\u7c7b\u578b */
  @Override
  @CacheEvict(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "T(com.njyzsz.common.cache.support.CacheKeyBuilder).build('system','dict:type','all')")
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(DictTypeDTO dto) {
    checkDuplicateTypeCode(dto);
    boolean updated = dictRepository.updateTypeById(dto);
    if (updated) {
      publishDictTypeChangedEvent(dto.getTypeCode(), "\u66f4\u65b0\u5b57\u5178\u7c7b\u578b");
      dictChangeEventPublisher.publishDictTypeEvent(
          dto.getTypeCode(), DictChangeEventConstants.EVENT_TYPE_UPDATED);
    }
    return updated;
  }

  /** \u903b\u8f91\u5220\u9664\u5b57\u5178\u7c7b\u578b */
  @Override
  @CacheEvict(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "T(com.njyzsz.common.cache.support.CacheKeyBuilder).build('system','dict:type','all')")
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    DictTypeVO vo = dictRepository.findTypeById(id).orElse(null);
    if (vo == null) {
      return false;
    }
    long itemCount = dictRepository.countItemsByTypeCode(vo.getTypeCode());
    if (itemCount > 0) {
      throw BusinessException.of(SystemExceptionCode.DICT_TYPE_HAS_ITEMS)
          .data("typeCode", vo.getTypeCode())
          .data("itemCount", itemCount);
    }
    boolean removed = dictRepository.deleteTypeById(id);
    if (removed) {
      publishDictTypeChangedEvent(vo.getTypeCode(), "\u5220\u9664\u5b57\u5178\u7c7b\u578b");
      dictChangeEventPublisher.publishDictTypeEvent(
          vo.getTypeCode(), DictChangeEventConstants.EVENT_TYPE_DELETED);
    }
    return removed;
  }

  // ============================== \u79c1\u6709\u65b9\u6cd5 ==============================

  /** \u5e7f\u64ad\u5b57\u5178\u7c7b\u578b\u53d8\u66f4\u4e8b\u4ef6 */
  private void publishDictTypeChangedEvent(String typeCode, String action) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("DictType")
            .aggregateId(typeCode)
            .eventType(DomainEventTypes.DICT_TYPE_CHANGED)
            .metadata("typeCode", typeCode)
            .metadata("action", action)
            .build());
  }

  // ============================== \u4e1a\u52a1\u67e5\u8be2 ==============================

  /** \u67e5\u8be2\u5168\u90e8\u5b57\u5178\u7c7b\u578b */
  @Override
  @YdszCacheable(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "T(com.njyzsz.common.cache.support.CacheKeyBuilder).build('system','dict:type','all')")
  public List<DictTypeVO> listAll() {
    return dictRepository.findAllTypes();
  }

  // ============================== \u79c1\u6709\u65b9\u6cd5 ==============================

  /** \u552f\u4e00\u6027\u6821\u9a8c */
  private void checkDuplicateTypeCode(DictTypeDTO dto) {
    if (dictRepository.existsTypeCode(dto.getTypeCode(), dto.getId())) {
      throw BusinessException.of(SystemExceptionCode.DICT_TYPE_CODE_DUPLICATE)
          .data("typeCode", dto.getTypeCode());
    }
  }
}

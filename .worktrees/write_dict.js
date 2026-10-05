const fs = require('fs');
const path = process.argv[2];
const spel = process.argv[3];

const content = \package com.njyzsz.system.server.service.impl;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njyzsz.common.cache.spring.YdszCacheable;
import com.njyzsz.system.server.constant.SystemCacheConstants;
import com.njyzsz.common.core.response.PageResponse;
import com.njyzsz.common.event.api.DomainEvent;
import com.njyzsz.common.event.api.DomainEventTypes;
import com.njyzsz.common.event.publish.DomainEventPublisher;
import com.njyzsz.common.exception.custom.BusinessException;
import com.njyzsz.system.domain.dto.DictTypeDTO;
import com.njyzsz.system.domain.enums.SystemExceptionCode;
import com.njyzsz.system.domain.query.DictPageQuery;
import com.njyzsz.system.domain.repository.DictRepository;
import com.njyzsz.system.domain.vo.DictTypeVO;
import com.njyzsz.system.server.service.DictService;
import com.njyzsz.system.server.service.event.DictChangeEventConstants;
import com.njyzsz.system.server.service.event.DictChangeEventPublisher;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

  /** 字典仓储（聚合 DictTypeMapper / DictItemMapper） */
  private final DictRepository dictRepository;

  /** 统一领域事件发布门面（ObjectProvider 可选注入，common-event 未引入时安全降级） */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** Redis 字典变更事件发布器（用于 SSE 多端广播） */
  private final DictChangeEventPublisher dictChangeEventPublisher;

  // ============================== CRUD ==============================

  /**
   * 分页查询字典类型（管理后台列表页）
   *
   * <p>支持按 typeCode 精确匹配、typeName 模糊匹配、status 精确匹配过滤，按 created_at 倒序。
   *
   * @param query 分页查询条件
   * @return 分页结果
   */
  @Override
  public PageResponse<List<DictTypeVO>> page(DictPageQuery query) {
    return dictRepository.findTypePage(query);
  }

  /**
   * 根据主键查询字典类型
   *
   * @param id 字典类型主键
   * @return 字典类型 VO，不存在返回 null
   */
  @Override
  public DictTypeVO getById(String id) {
    return dictRepository.findTypeById(id).orElse(null);
  }

  /**
   * 新增字典类型
   *
   * <p>执行链路：唯一性校验 → 插入 DB → 发布领域事件 + Redis 通道事件（双通道）
   *
   * @return 新创建的字典类型 ID
   */
  @Override
  @CacheEvict(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "\")
  @Transactional(rollbackFor = Exception.class)
  public String save(DictTypeDTO dto) {
    checkDuplicateTypeCode(dto);
    dictRepository.insertType(dto);
    publishDictTypeChangedEvent(dto.getTypeCode(), "创建字典类型");
    dictChangeEventPublisher.publishDictTypeEvent(
        dto.getTypeCode(), DictChangeEventConstants.EVENT_TYPE_CREATED);
    return dto.getId();
  }

  /**
   * 更新字典类型
   *
   * <p>执行链路：唯一性校验 → 更新 DB → 发布领域事件 + Redis 通道事件
   *
   * @return true=更新成功，false=记录不存在
   */
  @Override
  @CacheEvict(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "\")
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(DictTypeDTO dto) {
    checkDuplicateTypeCode(dto);
    boolean updated = dictRepository.updateTypeById(dto);
    if (updated) {
      publishDictTypeChangedEvent(dto.getTypeCode(), "更新字典类型");
      dictChangeEventPublisher.publishDictTypeEvent(
          dto.getTypeCode(), DictChangeEventConstants.EVENT_TYPE_UPDATED);
    }
    return updated;
  }

  /**
   * 逻辑删除字典类型
   *
   * <p>采用逻辑删除（deleted=1 + status=DISABLED）。
   *
   * <p><b>子项校验：</b>删除前校验其下是否存在字典项，若存在则抛出
   * {@link SystemExceptionCode#DICT_TYPE_HAS_ITEMS} 阻止删除。
   *
   * @param id 字典类型主键
   * @return true=删除成功，false=记录不存在
   */
  @Override
  @CacheEvict(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "\")
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    DictTypeVO vo = dictRepository.findTypeById(id).orElse(null);
    if (vo == null) {
      return false;
    }
    // 子项校验：若该类型下存在字典项，阻止删除
    long itemCount = dictRepository.countItemsByTypeCode(vo.getTypeCode());
    if (itemCount > 0) {
      throw BusinessException.of(SystemExceptionCode.DICT_TYPE_HAS_ITEMS)
          .data("typeCode", vo.getTypeCode())
          .data("itemCount", itemCount);
    }
    boolean removed = dictRepository.deleteTypeById(id);
    if (removed) {
      publishDictTypeChangedEvent(vo.getTypeCode(), "删除字典类型");
      dictChangeEventPublisher.publishDictTypeEvent(
          vo.getTypeCode(), DictChangeEventConstants.EVENT_TYPE_DELETED);
    }
    return removed;
  }

  // ============================== 私有方法 ==============================

  /**
   * 广播字典类型变更事件（用于跨实例本地缓存失效感知）。
   *
   * @param typeCode 字典类型编码
   * @param action 变更动作描述
   */
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

  // ============================== 业务查询 ==============================

  /**
   * 查询全部字典类型（不区分状态）
   *
   * <p>按照 createdAt 倒序返回，走本地 Caffeine 缓存（5min TTL）。
   *
   * @return 全部字典类型列表
   */
  @Override
  @YdszCacheable(
      value = SystemCacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "\")
  public List<DictTypeVO> listAll() {
    return dictRepository.findAllTypes();
  }

  // ============================== 私有方法 ==============================

  /**
   * 唯一性校验
   *
   * <p>校验 typeCode 是否已被其他字典类型占用。更新场景下排除自身 ID。
   *
   * @throws BusinessException typeCode 已存在时抛出
   */
  private void checkDuplicateTypeCode(DictTypeDTO dto) {
    if (dictRepository.existsTypeCode(dto.getTypeCode(), dto.getId())) {
      throw BusinessException.of(SystemExceptionCode.DICT_TYPE_CODE_DUPLICATE)
          .data("typeCode", dto.getTypeCode());
    }
  }
}
\;

fs.writeFileSync(path, content, 'utf-8');
console.log('Written ' + content.length + ' chars to ' + path);
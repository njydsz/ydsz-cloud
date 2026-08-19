package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.DictItem;
import com.njydsz.system.infra.entity.DictType;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.infra.mapper.DictTypeMapper;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * 字典仓储实现（Infra 层）。
 *
 * <p>实现 {@link DictRepository} 接口，封装 DictTypeMapper / DictItemMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link SystemConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link SystemConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class DictRepositoryImpl implements DictRepository {

  private final DictTypeMapper dictTypeMapper;

  private final DictItemMapper dictItemMapper;

  private final SystemConverter converter;

  // ============================== 字典类型 ==============================

  @Override
  public PageResponse<List<DictTypeVO>> findTypePage(DictPageQuery query) {
    Page<DictType> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
    if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
      wrapper.eq(DictType::getTypeCode, query.getTypeCode());
    }
    if (query.getTypeName() != null && !query.getTypeName().isBlank()) {
      wrapper.like(DictType::getTypeName, query.getTypeName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(DictType::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(DictType::getCreatedAt);
    com.baomidou.mybatisplus.core.metadata.IPage<DictType> result = dictTypeMapper.selectPage(page, wrapper);
    List<DictTypeVO> vos = converter.dictTypeListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  @Override
  public Optional<DictTypeVO> findTypeById(String id) {
    return Optional.ofNullable(dictTypeMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<DictTypeVO> findAllTypes() {
    return converter.dictTypeListToVO(dictTypeMapper.selectList(null));
  }

  @Override
  public boolean existsTypeCode(String typeCode, String excludeId) {
    LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictType::getTypeCode, typeCode);
    if (excludeId != null) {
      wrapper.ne(DictType::getId, excludeId);
    }
    return dictTypeMapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean insertType(DictTypeDTO dto) {
    DictType entity = converter.dtoToEntity(dto);
    return dictTypeMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateTypeById(DictTypeDTO dto) {
    DictType entity = converter.dtoToEntityWithId(dto);
    return dictTypeMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteTypeById(String id) {
    return dictTypeMapper.deleteById(id) > 0;
  }

  @Override
  public long countItemsByTypeCode(String typeCode) {
    Long count =
        dictItemMapper.selectCount(new LambdaQueryWrapper<DictItem>().eq(DictItem::getTypeCode, typeCode));
    return count != null ? count : 0L;
  }

  // ============================== 字典项 ==============================

  @Override
  public Optional<DictItemVO> findItemById(String id) {
    return Optional.ofNullable(dictItemMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<DictItemVO> findItemByTypeAndCode(String typeCode, String itemCode) {
    return Optional.ofNullable(dictItemMapper.selectByTypeAndCode(typeCode, itemCode))
        .map(converter::entityToVO);
  }

  @Override
  public List<DictItemVO> findItemsEnabledByTypeCode(String typeCode) {
    return converter.dictItemListToVO(dictItemMapper.listEnabledByTypeCode(typeCode));
  }

  @Override
  public List<DictItemVO> findItemsByParentId(String parentId) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItem::getParentId, parentId).orderByAsc(DictItem::getSortOrder);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findItemsByTypeCode(String typeCode) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItem::getTypeCode, typeCode).orderByAsc(DictItem::getSortOrder);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<DictItemVO>> findItemPage(DictItemPageQuery query) {
    Page<DictItem> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
      wrapper.eq(DictItem::getTypeCode, query.getTypeCode());
    }
    if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
      wrapper.like(DictItem::getItemCode, query.getItemCode());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(DictItem::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(DictItem::getCreatedAt);
    com.baomidou.mybatisplus.core.metadata.IPage<DictItem> result = dictItemMapper.selectPage(page, wrapper);
    List<DictItemVO> vos = converter.dictItemListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  @Override
  public List<DictItemVO> findAllItems() {
    return converter.dictItemListToVO(dictItemMapper.selectList(null));
  }

  @Override
  public List<DictItemVO> findItemsForExport(String typeCode) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItem::getDeleted, 0);
    if (typeCode != null && !typeCode.isBlank()) {
      wrapper.eq(DictItem::getTypeCode, typeCode);
    }
    wrapper.orderByAsc(DictItem::getTypeCode, DictItem::getSortOrder);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findItemsByTypeCodes(Set<String> typeCodes) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.select(DictItem::getTypeCode, DictItem::getItemCode).in(DictItem::getTypeCode, typeCodes);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public boolean existsItemByTypeAndCode(String typeCode, String itemCode) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItem::getTypeCode, typeCode).eq(DictItem::getItemCode, itemCode);
    return dictItemMapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean insertItem(DictItemDTO dto) {
    DictItem entity = converter.dtoToEntity(dto);
    return dictItemMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateItemById(DictItemDTO dto) {
    DictItem entity = converter.dtoToEntityWithId(dto);
    return dictItemMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteItemById(String id) {
    return dictItemMapper.deleteById(id) > 0;
  }

  @Override
  public boolean insertItemsBatch(List<DictItemDTO> items) {
    List<DictItem> entities = converter.dictItemDtosToEntities(items);
    return dictItemMapper.insertBatch(entities) > 0;
  }

  @Override
  public int physicalDeleteByTypeCode(String typeCode) {
    return dictItemMapper.physicalDeleteByTypeCode(typeCode);
  }

  @Override
  public List<DictItemVO> findEnabledItems() {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItem::getStatus, "ENABLED").eq(DictItem::getDeleted, 0);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findByTenantId(String tenantId) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItem::getDeleted, 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq(DictItem::getTenantId, tenantId);
    }
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }
}

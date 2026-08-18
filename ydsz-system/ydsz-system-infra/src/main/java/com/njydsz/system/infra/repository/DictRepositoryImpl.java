package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
  public IPage<DictTypeVO> findTypePage(DictPageQuery query) {
    Page<DictType> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<DictType> wrapper = new QueryWrapper<>();
    if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
      wrapper.eq("type_code", query.getTypeCode());
    }
    if (query.getTypeName() != null && !query.getTypeName().isBlank()) {
      wrapper.like("type_name", query.getTypeName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("created_at");
    IPage<DictType> entityPage = dictTypeMapper.selectPage(page, wrapper);
    // DO → VO 转换
    List<DictTypeVO> vos = converter.dictTypeListToVO(entityPage.getRecords());
    Page<DictTypeVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    voPage.setRecords(vos);
    return voPage;
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
    QueryWrapper<DictType> wrapper = new QueryWrapper<>();
    wrapper.eq("type_code", typeCode);
    if (excludeId != null) {
      wrapper.ne("id", excludeId);
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
        dictItemMapper.selectCount(new QueryWrapper<DictItem>().eq("type_code", typeCode));
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
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("parent_id", parentId).orderByAsc("sort_order");
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findItemsByTypeCode(String typeCode) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("type_code", typeCode).orderByAsc("sort_order");
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public IPage<DictItemVO> findItemPage(DictItemPageQuery query) {
    Page<DictItem> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
      wrapper.eq("type_code", query.getTypeCode());
    }
    if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
      wrapper.like("item_code", query.getItemCode());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("created_at");
    IPage<DictItem> entityPage = dictItemMapper.selectPage(page, wrapper);
    // DO → VO 转换
    List<DictItemVO> vos = converter.dictItemListToVO(entityPage.getRecords());
    Page<DictItemVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    voPage.setRecords(vos);
    return voPage;
  }

  @Override
  public List<DictItemVO> findAllItems() {
    return converter.dictItemListToVO(dictItemMapper.selectList(null));
  }

  @Override
  public List<DictItemVO> findItemsForExport(String typeCode) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (typeCode != null && !typeCode.isBlank()) {
      wrapper.eq("type_code", typeCode);
    }
    wrapper.orderByAsc("type_code", "sort_order");
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findItemsByTypeCodes(Set<String> typeCodes) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.select("type_code", "item_code").in("type_code", typeCodes);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public boolean existsItemByTypeAndCode(String typeCode, String itemCode) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("type_code", typeCode).eq("item_code", itemCode);
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
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("status", "ENABLED").eq("deleted", 0);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findByTenantId(String tenantId) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq("tenant_id", tenantId);
    }
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }
}

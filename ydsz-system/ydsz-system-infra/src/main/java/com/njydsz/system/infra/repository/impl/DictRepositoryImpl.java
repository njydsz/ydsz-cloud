package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.entity.DictType;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.infra.mapper.DictTypeMapper;
import com.njydsz.system.infra.repository.DictRepository;

/**
 * 字典仓储实现（Infra 层）。
 *
 * <p>实现 {@link DictRepository} 接口，封装 DictTypeMapper / DictItemMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>返回领域实体，由 Service 层负责转换为 VO
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

  // ============================== 字典类型 ==============================

  @Override
  public IPage<DictType> findTypePage(
      Page<DictType> page, String typeCode, String typeName, String status) {
    QueryWrapper<DictType> wrapper = new QueryWrapper<>();
    if (typeCode != null && !typeCode.isBlank()) {
      wrapper.eq("type_code", typeCode);
    }
    if (typeName != null && !typeName.isBlank()) {
      wrapper.like("type_name", typeName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    return dictTypeMapper.selectPage(page, wrapper);
  }

  @Override
  public Optional<DictType> findTypeById(String id) {
    return Optional.ofNullable(dictTypeMapper.selectById(id));
  }

  @Override
  public List<DictType> findAllTypes() {
    return dictTypeMapper.selectList(null);
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
  public boolean insertType(DictType entity) {
    return dictTypeMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateTypeById(DictType entity) {
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
  public Optional<DictItem> findItemById(String id) {
    return Optional.ofNullable(dictItemMapper.selectById(id));
  }

  @Override
  public Optional<DictItem> findItemByTypeAndCode(String typeCode, String itemCode) {
    return Optional.ofNullable(dictItemMapper.selectByTypeAndCode(typeCode, itemCode));
  }

  @Override
  public List<DictItem> findItemsEnabledByTypeCode(String typeCode) {
    return dictItemMapper.listEnabledByTypeCode(typeCode);
  }

  @Override
  public List<DictItem> findItemsByParentId(String parentId) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("parent_id", parentId).orderByAsc("sort_order");
    return dictItemMapper.selectList(wrapper);
  }

  @Override
  public List<DictItem> findItemsByTypeCode(String typeCode) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("type_code", typeCode).orderByAsc("sort_order");
    return dictItemMapper.selectList(wrapper);
  }

  @Override
  public IPage<DictItem> findItemPage(
      Page<DictItem> page, String typeCode, String itemCode, String status) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    if (typeCode != null && !typeCode.isBlank()) {
      wrapper.eq("type_code", typeCode);
    }
    if (itemCode != null && !itemCode.isBlank()) {
      wrapper.like("item_code", itemCode);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    return dictItemMapper.selectPage(page, wrapper);
  }

  @Override
  public List<DictItem> findAllItems() {
    return dictItemMapper.selectList(null);
  }

  @Override
  public List<DictItem> findItemsForExport(String typeCode) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (typeCode != null && !typeCode.isBlank()) {
      wrapper.eq("type_code", typeCode);
    }
    wrapper.orderByAsc("type_code", "sort_order");
    return dictItemMapper.selectList(wrapper);
  }

  @Override
  public List<DictItem> findItemsByTypeCodes(Set<String> typeCodes) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.select("type_code", "item_code").in("type_code", typeCodes);
    return dictItemMapper.selectList(wrapper);
  }

  @Override
  public boolean existsItemByTypeAndCode(String typeCode, String itemCode) {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("type_code", typeCode).eq("item_code", itemCode);
    return dictItemMapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean insertItem(DictItem entity) {
    return dictItemMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateItemById(DictItem entity) {
    return dictItemMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteItemById(String id) {
    return dictItemMapper.deleteById(id) > 0;
  }

  @Override
  public boolean insertItemsBatch(List<DictItem> items) {
    return dictItemMapper.insertBatch(items) > 0;
  }

  @Override
  public int physicalDeleteByTypeCode(String typeCode) {
    return dictItemMapper.physicalDeleteByTypeCode(typeCode);
  }

  @Override
  public List<DictItem> findEnabledItems() {
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.eq("status", "ENABLED").eq("deleted", 0);
    return dictItemMapper.selectList(wrapper);
  }
}

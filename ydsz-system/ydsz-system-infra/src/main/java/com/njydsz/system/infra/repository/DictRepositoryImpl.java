package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.DictItemDO;
import com.njydsz.system.infra.entity.DictTypeDO;
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
    Page<DictTypeDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<DictTypeDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
      wrapper.eq(DictTypeDO::getTypeCode, query.getTypeCode());
    }
    if (query.getTypeName() != null && !query.getTypeName().isBlank()) {
      wrapper.like(DictTypeDO::getTypeName, query.getTypeName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(DictTypeDO::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(DictTypeDO::getCreatedAt);
    com.baomidou.mybatisplus.core.metadata.IPage<DictTypeDO> result = dictTypeMapper.selectPage(page, wrapper);
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
    LambdaQueryWrapper<DictTypeDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictTypeDO::getTypeCode, typeCode);
    if (excludeId != null) {
      wrapper.ne(DictTypeDO::getId, excludeId);
    }
    return dictTypeMapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean insertType(DictTypeDTO dto) {
    DictTypeDO entity = converter.dtoToEntity(dto);
    return dictTypeMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateTypeById(DictTypeDTO dto) {
    DictTypeDO entity = converter.dtoToEntityWithId(dto);
    return dictTypeMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteTypeById(String id) {
    return dictTypeMapper.deleteById(id) > 0;
  }

  @Override
  public long countItemsByTypeCode(String typeCode) {
    Long count =
        dictItemMapper.selectCount(new LambdaQueryWrapper<DictItemDO>().eq(DictItemDO::getTypeCode, typeCode));
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
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItemDO::getParentId, parentId).orderByAsc(DictItemDO::getSortOrder);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findItemsByTypeCode(String typeCode) {
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItemDO::getTypeCode, typeCode).orderByAsc(DictItemDO::getSortOrder);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<DictItemVO>> findItemPage(DictItemPageQuery query) {
    Page<DictItemDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
      wrapper.eq(DictItemDO::getTypeCode, query.getTypeCode());
    }
    if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
      wrapper.like(DictItemDO::getItemCode, query.getItemCode());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(DictItemDO::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(DictItemDO::getCreatedAt);
    com.baomidou.mybatisplus.core.metadata.IPage<DictItemDO> result = dictItemMapper.selectPage(page, wrapper);
    List<DictItemVO> vos = converter.dictItemListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  @Override
  public List<DictItemVO> findAllItems() {
    return converter.dictItemListToVO(dictItemMapper.selectList(null));
  }

  @Override
  public List<DictItemVO> findItemsForExport(String typeCode) {
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItemDO::getDeleted, 0);
    if (typeCode != null && !typeCode.isBlank()) {
      wrapper.eq(DictItemDO::getTypeCode, typeCode);
    }
    wrapper.orderByAsc(DictItemDO::getTypeCode, DictItemDO::getSortOrder);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public List<DictItemVO> findItemsByTypeCodes(Set<String> typeCodes) {
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.select(DictItemDO::getTypeCode, DictItemDO::getItemCode).in(DictItemDO::getTypeCode, typeCodes);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }

  @Override
  public boolean existsItemByTypeAndCode(String typeCode, String itemCode) {
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItemDO::getTypeCode, typeCode).eq(DictItemDO::getItemCode, itemCode);
    return dictItemMapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean insertItem(DictItemDTO dto) {
    DictItemDO entity = converter.dtoToEntity(dto);
    return dictItemMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateItemById(DictItemDTO dto) {
    DictItemDO entity = converter.dtoToEntityWithId(dto);
    return dictItemMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteItemById(String id) {
    return dictItemMapper.deleteById(id) > 0;
  }

  @Override
  public boolean insertItemsBatch(List<DictItemDTO> items) {
    List<DictItemDO> entities = converter.dictItemDtosToEntities(items);
    return dictItemMapper.insertBatch(entities) > 0;
  }

  @Override
  public int physicalDeleteByTypeCode(String typeCode) {
    return dictItemMapper.physicalDeleteByTypeCode(typeCode);
  }

  @Override
  public List<DictItemVO> findEnabledItems() {
    LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DictItemDO::getStatus, "ENABLED").eq(DictItemDO::getDeleted, 0);
    return converter.dictItemListToVO(dictItemMapper.selectList(wrapper));
  }
}

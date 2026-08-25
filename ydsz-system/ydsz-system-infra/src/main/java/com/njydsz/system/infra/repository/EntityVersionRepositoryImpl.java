package com.njydsz.system.infra.repository;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.EntityVersionDTO;
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.domain.repository.EntityVersionRepository;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.EntityVersion;
import com.njydsz.system.infra.mapper.EntityVersionMapper;




/**
 * 统一实体版本仓储实现（Infra 层）。
 *
 * <p>实现 {@link EntityVersionRepository} 接口，封装 {@link EntityVersionMapper} 数据访问细节。
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
public class EntityVersionRepositoryImpl implements EntityVersionRepository {

  private final EntityVersionMapper entityVersionMapper;

  private final SystemConverter converter;

  @Override
  public List<EntityVersionVO> findByTypeAndKey(String resourceType, String resourceKey) {
    return converter.entityVersionListToVO(
        entityVersionMapper.listByResourceTypeAndKey(resourceType, resourceKey));
  }

  @Override
  public PageResponse<List<EntityVersionVO>> findPageByTypeAndKey(EntityVersionPageQuery query) {
    Page<EntityVersion> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<EntityVersion> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(EntityVersion::getResourceType, query.getResourceType())
        .eq(EntityVersion::getResourceKey, query.getResourceKey())
        .orderByDesc(EntityVersion::getEffectiveDate);
    com.baomidou.mybatisplus.core.metadata.IPage<EntityVersion> result =
        entityVersionMapper.selectPage(page, wrapper);
    List<EntityVersionVO> vos = converter.entityVersionListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public Optional<EntityVersionVO> findByTypeAndKeyAndVersion(
      String resourceType, String resourceKey, String version) {
    return Optional.ofNullable(
        entityVersionMapper.selectByTypeAndKeyAndVersion(resourceType, resourceKey, version))
        .map(converter::entityVersionToVO);
  }

  @Override
  public EntityVersionVO save(EntityVersionDTO dto) {
    EntityVersion entity = converter.dtoToEntity(dto);
    entityVersionMapper.insert(entity);
    return converter.entityVersionToVO(entity);
  }
}

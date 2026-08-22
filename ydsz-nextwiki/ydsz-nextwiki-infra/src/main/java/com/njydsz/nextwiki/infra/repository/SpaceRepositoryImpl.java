package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.SpaceDTO;
import com.njydsz.nextwiki.domain.repository.SpaceRepository;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.SpaceDO;
import com.njydsz.nextwiki.infra.mapper.SpaceMapper;

/**
 * 知识库空间仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link NextwikiConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SpaceRepositoryImpl implements SpaceRepository {

  private final SpaceMapper spaceMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiConverter nextwikiConverter;

  @Override
  public int save(SpaceDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    SpaceDO entity = nextwikiConverter.toSpaceDO(dto);
    return spaceMapper.insert(entity);
  }

  @Override
  public int update(SpaceDTO dto) {
    SpaceDO entity = nextwikiConverter.toSpaceDO(dto);
    return spaceMapper.updateById(entity);
  }

  @Override
  public Optional<SpaceVO> findById(String id) {
    SpaceDO entity = spaceMapper.selectById(id);
    return Optional.ofNullable(entity).map(nextwikiConverter::entityToVO);
  }

  @Override
  public Optional<SpaceVO> findByTenantIdAndName(String tenantId, String name) {
    SpaceDO entity = spaceMapper.selectByTenantIdAndName(tenantId, name);
    return Optional.ofNullable(entity).map(nextwikiConverter::entityToVO);
  }

  @Override
  public List<SpaceVO> findByTenantId(String tenantId) {
    List<SpaceDO> entities = spaceMapper.selectByTenantId(tenantId);
    return nextwikiConverter.spaceListToVO(entities);
  }

  @Override
  public PageResponse<List<SpaceVO>> findByTenantIdWithPage(String tenantId, int offset, int limit) {
    Page<SpaceDO> pageParam = new Page<>(offset / limit + 1, limit);
    IPage<SpaceDO> result = spaceMapper.selectByTenantIdWithPage(pageParam, tenantId, offset, limit);
    List<SpaceVO> vos = nextwikiConverter.spaceListToVO(result.getRecords());
    Page<SpaceVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }

  @Override
  public int countByTenantId(String tenantId) {
    return spaceMapper.countByTenantId(tenantId);
  }

  @Override
  public int deleteById(String id) {
    return spaceMapper.deleteById(id);
  }
}

package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.SpaceDTO;
import com.njydsz.nextwiki.domain.entity.Space;
import com.njydsz.nextwiki.domain.repository.SpaceRepository;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.infra.mapper.SpaceMapper;

/**
 * 知识库空间仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SpaceRepositoryImpl implements SpaceRepository {

  /** 空间 MyBatis Mapper（数据库 CRUD 原始操作） */
  private final SpaceMapper spaceMapper;

  /** 分布式 ID 生成器（Snowflake 算法，生成空间唯一 ID） */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** DTO/VO/DO 转换器（实体与视图对象之间的映射） */
  private final NextwikiStructMapper mapper;

  @Override
  public int save(SpaceDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    Space entity = mapper.spaceToEntity(dto);
    return spaceMapper.insert(entity);
  }

  @Override
  public int update(SpaceDTO dto) {
    Space entity = mapper.spaceToEntity(dto);
    return spaceMapper.updateById(entity);
  }

  @Override
  public Optional<SpaceVO> findById(String id) {
    Space entity = spaceMapper.selectById(id);
    return Optional.ofNullable(entity).map(mapper::spaceToVO);
  }

  @Override
  public Optional<SpaceVO> findByTenantIdAndName(String tenantId, String name) {
    Space entity = spaceMapper.selectByTenantIdAndName(tenantId, name);
    return Optional.ofNullable(entity).map(mapper::spaceToVO);
  }

  /**
   * 按租户 ID 查询空间列表（不分页）。
   *
   * @param tenantId 租户 ID
   * @return 空间视图列表
   */
  @Override
  public List<SpaceVO> findByTenantId(String tenantId) {
    List<Space> entities = spaceMapper.selectByTenantId(tenantId);
    return mapper.spaceListToVO(entities);
  }

  /**
   * 按租户 ID 分页查询空间列表。
   *
   * @param tenantId 租户 ID
   * @param offset 分页偏移量
   * @param limit 每页条数
   * @return 分页空间视图对象列表
   */
  @Override
  public PageResponse<List<SpaceVO>> findByTenantIdWithPage(String tenantId, int offset, int limit) {
    Page<Space> pageParam = new Page<>(offset / limit + 1, limit);
    IPage<Space> result = spaceMapper.selectByTenantIdWithPage(pageParam, tenantId, offset, limit);
    List<SpaceVO> vos = mapper.spaceListToVO(result.getRecords());
    Page<SpaceVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }

  /**
   * 统计租户下的空间数量。
   *
   * @param tenantId 租户 ID
   * @return 空间数量
   */
  @Override
  public int countByTenantId(String tenantId) {
    return spaceMapper.countByTenantId(tenantId);
  }

  /**
   * 逻辑删除指定空间。
   *
   * @param id 空间 ID
   * @return 更新记录数
   */
  @Override
  public int deleteById(String id) {
    return spaceMapper.deleteById(id);
  }
}

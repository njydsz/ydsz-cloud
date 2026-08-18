package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.TrashItemDO;
import com.njydsz.nextwiki.infra.mapper.TrashItemMapper;

/**
 * 回收站仓储实现
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
public class TrashItemRepositoryImpl implements TrashItemRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final TrashItemMapper trashItemMapper;
  private final NextwikiConverter converter;

  @Override
  public TrashItemVO save(TrashItemDTO dto) {
    TrashItemDO entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    trashItemMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int saveBatch(List<TrashItemDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }
    List<TrashItemDO> entities = converter.trashItemDtosToEntities(dtos);
    int count = 0;
    for (TrashItemDO entity : entities) {
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      }
      count++;
    }
    trashItemMapper.insertBatch(entities);
    return count;
  }

  @Override
  public Optional<TrashItemVO> findById(String id) {
    return Optional.ofNullable(trashItemMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<TrashItemVO> findByFileNodeId(String fileNodeId) {
    return Optional.ofNullable(trashItemMapper.selectByFileNodeId(fileNodeId))
        .map(converter::entityToVO);
  }

  @Override
  public List<TrashItemVO> findActiveTrash(String userId) {
    return converter.trashItemListToVO(trashItemMapper.selectActiveTrash(userId));
  }

  @Override
  public List<TrashItemVO> findExpiredItems(int limit) {
    return converter.trashItemListToVO(trashItemMapper.selectExpiredItems(limit));
  }

  @Override
  public void update(TrashItemDTO dto) {
    TrashItemDO entity = converter.dtoToEntityWithId(dto);
    trashItemMapper.updateById(entity);
  }

  @Override
  public void deleteById(String id) {
    trashItemMapper.deleteById(id);
  }

  @Override
  public int countActiveTrash(String userId) {
    return trashItemMapper.countActiveTrash(userId);
  }
}

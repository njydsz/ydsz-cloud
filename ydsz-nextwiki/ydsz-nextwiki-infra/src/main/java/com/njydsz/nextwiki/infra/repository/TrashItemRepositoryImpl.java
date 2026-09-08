package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;
import com.njydsz.nextwiki.infra.mapper.TrashItemMapper;

/**
 * 回收站仓储实现
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
public class TrashItemRepositoryImpl implements TrashItemRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final TrashItemMapper trashItemMapper;
  private final NextwikiStructMapper mapper;

  /**
   * 保存回收站条目（文件移入回收站时调用）。
   *
   * @param dto 回收站条目数据传输对象
   * @return 保存后的回收站条目视图对象
   */
  @Override
  public TrashItemVO save(TrashItemDTO dto) {
    TrashItem entity = mapper.trashItemToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    trashItemMapper.insert(entity);
    return mapper.trashItemToVO(entity);
  }

  /**
   * 批量新增回收站条目（文件夹级联删除）。
   *
   * @param dtos 回收站条目数据传输对象列表
   * @return 实际保存的记录数
   */
  @Override
  public int saveBatch(List<TrashItemDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }
    List<TrashItem> entities = mapper.trashItemListToEntity(dtos);
    int count = 0;
    for (TrashItem entity : entities) {
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      }
      count++;
    }
    trashItemMapper.insertBatch(entities);
    return count;
  }

  /**
   * 按 ID 查询回收站条目。
   *
   * @param id 回收站条目 ID
   * @return 回收站条目视图对象（可能为空）
   */
  @Override
  public Optional<TrashItemVO> findById(String id) {
    return Optional.ofNullable(trashItemMapper.selectById(id)).map(mapper::trashItemToVO);
  }

  /**
   * 按原始文件节点 ID 查询回收站条目。
   *
   * @param fileNodeId 原始文件节点 ID
   * @return 回收站条目视图对象（可能为空）
   */
  @Override
  public Optional<TrashItemVO> findByFileNodeId(String fileNodeId) {
    return Optional.ofNullable(trashItemMapper.findByFileNodeId(fileNodeId))
        .map(mapper::trashItemToVO);
  }

  /**
   * 查询用户的所有未清理回收站条目。
   *
   * @param userId 用户 ID
   * @return 回收站条目视图对象列表
   */
  @Override
  public List<TrashItemVO> findActiveTrash(String userId) {
    return mapper.trashItemListToVO(trashItemMapper.findActiveTrash(userId));
  }

  /**
   * 查询已过期的回收站条目（定时任务清理用）。
   *
   * @param limit 每次查询的最大数量
   * @return 已过期条目视图列表
   */
  @Override
  public List<TrashItemVO> findExpiredItems(int limit) {
    return mapper.trashItemListToVO(trashItemMapper.findExpiredItems(limit));
  }

  /**
   * 更新回收站条目（恢复后更新状态等）。
   *
   * @param dto 回收站条目数据传输对象
   */
  @Override
  public void update(TrashItemDTO dto) {
    TrashItem entity = mapper.trashItemToEntity(dto);
    trashItemMapper.updateById(entity);
  }

  /**
   * 物理删除回收站条目。
   *
   * @param id 回收站条目 ID
   */
  @Override
  public void deleteById(String id) {
    trashItemMapper.deleteById(id);
  }

  /**
   * 统计用户有效的回收站条目数量。
   *
   * @param userId 用户 ID
   * @return 有效条目数量
   */
  @Override
  public int countActiveTrash(String userId) {
    return trashItemMapper.countActiveTrash(userId);
  }
}

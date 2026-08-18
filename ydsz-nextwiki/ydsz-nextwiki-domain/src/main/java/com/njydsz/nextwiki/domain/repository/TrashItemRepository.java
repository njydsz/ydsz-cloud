package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;

/**
 * 回收站仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link TrashItemVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link TrashItemDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TrashItemRepository {

  /**
   * 保存回收站条目（新增或更新）
   *
   * @param dto 回收站条目 DTO
   * @return 持久化后的回收站条目 VO
   */
  TrashItemVO save(TrashItemDTO dto);

  /**
   * 批量保存回收站条目（用于批量删除场景）。
   *
   * <p>一次性插入多条记录，比逐条插入性能更优。
   *
   * @param dtos 回收站条目 DTO 列表
   * @return 实际插入条数
   */
  int saveBatch(List<TrashItemDTO> dtos);

  /**
   * 按 ID 查询回收站条目
   *
   * @param id 回收站条目ID
   * @return 回收站条目 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TrashItemVO> findById(String id);

  /**
   * 按原文件节点 ID 查询对应的回收站条目
   *
   * @param fileNodeId 原文件节点ID
   * @return 回收站条目 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TrashItemVO> findByFileNodeId(String fileNodeId);

  /**
   * 查询某用户的全部未清理回收站条目
   *
   * @param userId 用户ID
   * @return 活跃回收站条目 VO 列表
   */
  List<TrashItemVO> findActiveTrash(String userId);

  /**
   * 分页查询已超过保留期、待永久清理的回收站条目
   *
   * @param limit 单次最多返回条数
   * @return 待清理条目 VO 列表
   */
  List<TrashItemVO> findExpiredItems(int limit);

  /**
   * 更新回收站条目
   *
   * @param dto 回收站条目 DTO
   */
  void update(TrashItemDTO dto);

  /**
   * 物理删除回收站条目
   *
   * @param id 回收站条目ID
   */
  void deleteById(String id);

  /**
   * 统计某用户的活跃回收站条目数量
   *
   * @param userId 用户ID
   * @return 活跃条目数量
   */
  int countActiveTrash(String userId);
}

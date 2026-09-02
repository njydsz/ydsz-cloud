package com.njydsz.nextwiki.domain.repository;

import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;

/**
 * 存储配额仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link StorageQuotaVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link StorageQuotaDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface StorageQuotaRepository {

  /**
   * 保存存储配额记录（新增或更新）
   *
   * @param dto 存储配额 DTO
   * @return 持久化后的配额 VO
   */
  StorageQuotaVO save(StorageQuotaDTO dto);

  /**
   * 按 ID 查询配额记录
   *
   * @param id 配额记录ID
   * @return 配额 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<StorageQuotaVO> findById(String id);

  /**
   * 按维度（scopeType + scopeId）查询配额记录
   *
   * @param scopeType 配额维度
   * @param scopeId 维度ID
   * @return 配额 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<StorageQuotaVO> findByScope(String scopeType, String scopeId);

  /**
   * 原子增加已使用量
   *
   * @param scopeType 配额维度
   * @param scopeId 维度ID
   * @param bytesDelta 字节变化量
   * @param fileCountDelta 文件数变化量
   * @return 受影响行数
   */
  int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);

  /**
   * 原子减少已使用量
   *
   * @param scopeType 配额维度
   * @param scopeId 维度ID
   * @param bytesDelta 字节变化量
   * @param fileCountDelta 文件数变化量
   * @return 受影响行数
   */
  int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);
}

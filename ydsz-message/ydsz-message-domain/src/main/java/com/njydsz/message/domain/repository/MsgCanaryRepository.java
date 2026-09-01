package com.njydsz.message.domain.repository;

import java.util.Optional;

import com.njydsz.message.domain.dto.MsgCanaryDTO;
import com.njydsz.message.domain.vo.MsgCanaryVO;

/**
 * 灰度实验仓储接口（domain 层契约）。
 *
 * <p>定义灰度实验的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>CUD 入参使用领域 DTO（{@link MsgCanaryDTO}）</li>
 *   <li>返回值使用领域 VO（{@link MsgCanaryVO}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgCanaryRepository {

  /**
   * 保存灰度实验（插入）。
   *
   * @param dto 灰度实验 DTO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgCanaryDTO dto);

  /**
   * 更新灰度实验。
   *
   * @param dto 灰度实验 DTO（必须包含主键 ID）
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgCanaryDTO dto);

  /**
   * 根据主键 ID 查询灰度实验。
   *
   * @param id 实验 ID
   * @return 灰度实验 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgCanaryVO> findById(String id);

  /**
   * 根据实验唯一键查询灰度实验。
   *
   * @param canaryKey 实验唯一键
   * @return 灰度实验 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgCanaryVO> findByCanaryKey(String canaryKey);
}

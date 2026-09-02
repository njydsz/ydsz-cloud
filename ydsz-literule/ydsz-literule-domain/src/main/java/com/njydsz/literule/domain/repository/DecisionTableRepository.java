package com.njydsz.literule.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.literule.domain.dto.DecisionTableDTO;
import com.njydsz.literule.domain.vo.DecisionTableVO;

/**
 * 决策表 Repository（domain 层契约）。
 *
 * <p>定义决策表的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link DecisionTableVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DecisionTableRepository {

  /**
   * 查询全部决策表。
   *
   * @return 决策表 VO 列表
   */
  List<DecisionTableVO> findAll();

  /**
   * 根据决策表编码查询。
   *
   * @param tableCode 决策表编码
   * @return 决策表 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<DecisionTableVO> findByTableCode(String tableCode);

  /**
   * 保存决策表（新增或更新）。
   *
   * <p>当 DTO 中包含 {@code id} 时执行更新，否则执行新增。
   *
   * @param dto 决策表保存 DTO
   * @return 保存后的决策表 VO
   */
  DecisionTableVO save(DecisionTableDTO dto);

  /**
   * 根据 ID 删除决策表。
   *
   * @param id 决策表 ID
   */
  void deleteById(String id);
}


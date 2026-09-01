package com.njydsz.literule.server.config;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import com.njydsz.literule.domain.dto.DecisionTableDTO;
import com.njydsz.literule.domain.repository.DecisionTableRepository;
import com.njydsz.literule.domain.vo.DecisionTableVO;

/**
 * 决策表查询服务（server 层，P1-12 收口 web 跳层）
 *
 * <p>将 {@link DecisionTableRepository} 的 CRUD 收敛到应用服务层，web Controller 不再直接依赖 domain
 * Repository（分层合规）。与 {@link DecisionTableAdminService}（引擎注册/评估/Excel 侧）职责互补。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@RequiredArgsConstructor
public class DecisionTableQueryService {

  private final DecisionTableRepository repository;

  /**
   * 查询全部决策表
   *
   * @return 决策表 VO 列表
   */
  public List<DecisionTableVO> findAll() {
    return repository.findAll();
  }

  /**
   * 按编码查询决策表
   *
   * @param tableCode 决策表编码
   * @return 决策表 VO（不存在时为空）
   */
  public Optional<DecisionTableVO> findByTableCode(String tableCode) {
    return repository.findByTableCode(tableCode);
  }

  /**
   * 保存决策表
   *
   * @param dto 决策表入参
   * @return 保存后的决策表 VO
   */
  public DecisionTableVO save(DecisionTableDTO dto) {
    return repository.save(dto);
  }

  /**
   * 删除决策表
   *
   * @param id 主键
   */
  public void deleteById(String id) {
    repository.deleteById(id);
  }
}



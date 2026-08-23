package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MsgTemplateDTO;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;

/**
 * 消息模板仓储接口（domain 层契约）。
 *
 * <p>定义消息模板的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgTemplateVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link TemplateQueryDTO}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link MsgTemplateDTO}），禁止 VO 混入
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgTemplateRepository {

  /**
   * 保存消息模板（插入或更新）。
   *
   * @param dto 消息模板 DTO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgTemplateDTO dto);

  /**
   * 根据主键查询消息模板。
   *
   * @param id 模板 ID
   * @return 消息模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgTemplateVO> findById(String id);

  /**
   * 更新消息模板。
   *
   * @param dto 消息模板 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgTemplateDTO dto);

  /**
   * 根据主键删除消息模板。
   *
   * @param id 模板 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 按条件查询单条消息模板。
   *
   * @param query 查询参数
   * @return 消息模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgTemplateVO> findOne(TemplateQueryDTO query);

  /**
   * 分页查询消息模板。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgTemplateVO>> findPage(TemplateQueryDTO query);
}

package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.message.domain.query.MsgTemplateVersionQuery;
import com.njydsz.message.domain.vo.MsgTemplateVersionVO;

/**
 * 消息模板版本历史仓储接口（domain 层契约）。
 *
 * <p>定义消息模板版本历史的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgTemplateVersionVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgTemplateVersionQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgTemplateVersionVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgTemplateVersionRepository {

  /**
   * 保存模板版本（插入或更新）。
   *
   * @param vo 模板版本 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgTemplateVersionVO vo);

  /**
   * 按条件查询模板版本列表。
   *
   * @param query 查询参数
   * @return 模板版本 VO 列表
   */
  List<MsgTemplateVersionVO> findList(MsgTemplateVersionQuery query);

  /**
   * 按条件查询单条模板版本。
   *
   * @param query 查询参数
   * @return 模板版本 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgTemplateVersionVO> findOne(MsgTemplateVersionQuery query);
}

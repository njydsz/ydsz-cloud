package com.njydsz.message.domain.repository;

import java.util.List;

import com.njydsz.message.domain.query.MsgVariableSourceQuery;
import com.njydsz.message.domain.vo.MsgVariableSourceVO;

/**
 * 消息变量数据源仓储接口（domain 层契约）。
 *
 * <p>定义消息变量数据源的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgVariableSourceVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgVariableSourceQuery}）或具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgVariableSourceRepository {

  /**
   * 按条件查询变量数据源列表。
   *
   * @param query 查询参数
   * @return 变量数据源 VO 列表
   */
  List<MsgVariableSourceVO> findList(MsgVariableSourceQuery query);
}

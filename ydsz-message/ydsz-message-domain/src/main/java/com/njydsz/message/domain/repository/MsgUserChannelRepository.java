package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.message.domain.query.MsgUserChannelQuery;
import com.njydsz.message.domain.vo.MsgUserChannelVO;

/**
 * 用户通道绑定仓储接口（domain 层契约）。
 *
 * <p>定义用户通道绑定的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgUserChannelVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgUserChannelQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgUserChannelVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgUserChannelRepository {

  /**
   * 保存通道绑定（插入或更新）。
   *
   * @param vo 通道绑定 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgUserChannelVO vo);

  /**
   * 更新通道绑定。
   *
   * @param vo 通道绑定 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgUserChannelVO vo);

  /**
   * 根据主键删除通道绑定。
   *
   * @param id 绑定 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 按条件查询单条通道绑定。
   *
   * @param query 查询参数
   * @return 通道绑定 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgUserChannelVO> findOne(MsgUserChannelQuery query);

  /**
   * 按条件查询通道绑定列表。
   *
   * @param query 查询参数
   * @return 通道绑定 VO 列表
   */
  List<MsgUserChannelVO> findList(MsgUserChannelQuery query);
}

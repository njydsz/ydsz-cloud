package com.njydsz.message.domain.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.njydsz.message.domain.query.MsgOfflineQuery;
import com.njydsz.message.domain.vo.MsgOfflineVO;

/**
 * 离线消息仓储接口（domain 层契约）。
 *
 * <p>定义离线消息的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgOfflineVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgOfflineQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgOfflineVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgOfflineRepository {

  /**
   * 批量保存离线消息。
   *
   * @param list 离线消息 VO 列表
   * @return 保存成功返回 {@code true}
   */
  boolean saveBatch(List<MsgOfflineVO> list);

  /**
   * 按用户批量标记已推送。
   *
   * @param userId 用户 ID
   * @return 更新行数
   */
  int markPushedByUser(String userId);

  /**
   * 清理过期消息（状态改为 EXPIRED）。
   *
   * @return 更新行数
   */
  int markExpired();

  /**
   * 按条件查询离线消息列表。
   *
   * @param query 查询参数
   * @return 离线消息 VO 列表
   */
  List<MsgOfflineVO> findList(MsgOfflineQuery query);

  /**
   * 分页查询离线消息。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  IPage<MsgOfflineVO> findPage(MsgOfflineQuery query);
}

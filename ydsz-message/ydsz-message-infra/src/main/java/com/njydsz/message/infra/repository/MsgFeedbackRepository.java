package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.config.MsgFeedback;

/**
 * 消息用户反馈 Repository。
 *
 * <p>封装 {@code ydsz_msg_feedback} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgFeedbackRepository {

  /**
   * 插入反馈记录。
   *
   * @param entity 反馈实体
   * @return 影响行数
   */
  int insert(MsgFeedback entity);

  /**
   * 按条件查询反馈列表。
   *
   * @param wrapper 查询条件
   * @return 反馈列表
   */
  List<MsgFeedback> selectList(LambdaQueryWrapper<MsgFeedback> wrapper);

  /**
   * 分页查询反馈。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgFeedback> selectPage(Page<MsgFeedback> page, LambdaQueryWrapper<MsgFeedback> wrapper);
}

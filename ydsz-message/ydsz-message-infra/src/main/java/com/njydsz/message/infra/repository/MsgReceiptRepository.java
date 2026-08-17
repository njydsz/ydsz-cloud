package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.receipt.MsgReceipt;

/**
 * 消息回执 Repository。
 *
 * <p>封装 {@code ydsz_msg_receipt} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgReceiptRepository {

  /**
   * 插入回执记录。
   *
   * @param entity 回执实体
   * @return 影响行数
   */
  int insert(MsgReceipt entity);

  /**
   * 按条件查询回执列表。
   *
   * @param wrapper 查询条件
   * @return 回执列表
   */
  List<MsgReceipt> selectList(LambdaQueryWrapper<MsgReceipt> wrapper);
}

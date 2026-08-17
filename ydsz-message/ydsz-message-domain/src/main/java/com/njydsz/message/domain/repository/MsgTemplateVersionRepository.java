package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.template.MsgTemplateVersion;

/**
 * 消息模板版本历史 Repository。
 *
 * <p>封装 {@code ydsz_msg_template_version} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgTemplateVersionRepository {

  /**
   * 插入模板版本。
   *
   * @param entity 模板版本实体
   * @return 影响行数
   */
  int insert(MsgTemplateVersion entity);

  /**
   * 按条件查询模板版本列表。
   *
   * @param wrapper 查询条件
   * @return 模板版本列表
   */
  List<MsgTemplateVersion> selectList(LambdaQueryWrapper<MsgTemplateVersion> wrapper);

  /**
   * 按条件查询单条模板版本。
   *
   * @param wrapper 查询条件
   * @return 模板版本实体，不存在返回 null
   */
  MsgTemplateVersion selectOne(LambdaQueryWrapper<MsgTemplateVersion> wrapper);
}

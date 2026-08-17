package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.template.MsgTemplate;

/**
 * 消息模板 Repository。
 *
 * <p>封装 {@code ydsz_msg_template} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgTemplateRepository {

  /**
   * 插入模板。
   *
   * @param entity 模板实体
   * @return 影响行数
   */
  int insert(MsgTemplate entity);

  /**
   * 按 ID 查询模板。
   *
   * @param id 模板 ID
   * @return 模板实体，不存在返回 null
   */
  MsgTemplate selectById(String id);

  /**
   * 按 ID 更新模板。
   *
   * @param entity 模板实体
   * @return 影响行数
   */
  int updateById(MsgTemplate entity);

  /**
   * 按 ID 删除模板。
   *
   * @param id 模板 ID
   * @return 影响行数
   */
  int deleteById(String id);

  /**
   * 按条件查询单条模板。
   *
   * @param wrapper 查询条件
   * @return 模板实体，不存在返回 null
   */
  MsgTemplate selectOne(LambdaQueryWrapper<MsgTemplate> wrapper);

  /**
   * 分页查询模板。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgTemplate> selectPage(Page<MsgTemplate> page, LambdaQueryWrapper<MsgTemplate> wrapper);
}

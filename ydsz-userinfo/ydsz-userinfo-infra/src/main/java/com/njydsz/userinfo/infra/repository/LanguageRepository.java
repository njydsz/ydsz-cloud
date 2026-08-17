package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.njydsz.userinfo.infra.entity.LanguageDO;

/**
 * 语言配置 Repository 接口
 *
 * <p>封装语言配置表（{@code ydsz_language}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LanguageRepository {

  /**
   * 根据 ID 查询语言配置。
   *
   * @param id 语言 ID
   * @return 语言实体，不存在时返回 null
   */
  LanguageDO findById(String id);

  /**
   * 根据语言编码查询语言配置。
   *
   * @param languageCode 语言编码
   * @return 语言实体，不存在时返回 null
   */
  LanguageDO findByLanguageCode(String languageCode);

  /**
   * 查询默认语言配置。
   *
   * @return 语言实体，不存在时返回 null
   */
  LanguageDO findDefault();

  /**
   * 分页查询语言配置。
   *
   * @param page MyBatis-Plus 分页对象
   * @param wrapper 查询条件
   * @return 分页结果
   */
  com.baomidou.mybatisplus.core.metadata.IPage<LanguageDO> page(
      com.baomidou.mybatisplus.extension.plugins.pagination.Page<LanguageDO> page,
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LanguageDO> wrapper);

  /**
   * 条件查询语言列表。
   *
   * @param wrapper 查询条件
   * @return 语言列表
   */
  List<LanguageDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LanguageDO> wrapper);

  /**
   * 保存语言配置（插入）。
   *
   * @param entity 语言实体
   * @return 插入影响的行数
   */
  int insert(LanguageDO entity);

  /**
   * 更新语言配置。
   *
   * @param entity 语言实体
   * @return 更新影响的行数
   */
  int updateById(LanguageDO entity);

  /**
   * 删除语言配置（逻辑删除）。
   *
   * @param id 语言 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 统计符合条件的语言数量。
   *
   * @param wrapper 查询条件
   * @return 语言数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LanguageDO> wrapper);
}

package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.infra.entity.CompanyDO;

/**
 * 公司 Repository 接口
 *
 * <p>封装公司表（{@code ydsz_company}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyRepository {

  /**
   * 根据 ID 查询公司。
   *
   * @param id 公司 ID
   * @return 公司实体，不存在时返回 null
   */
  CompanyDO findById(String id);

  /**
   * 根据公司编码查询公司。
   *
   * @param companyCode 公司编码
   * @return 公司实体，不存在时返回 null
   */
  CompanyDO findByCompanyCode(String companyCode);

  /**
   * 条件查询公司列表。
   *
   * @param wrapper 查询条件
   * @return 公司列表
   */
  List<CompanyDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanyDO> wrapper);

  /**
   * 批量根据 ID 查询公司。
   *
   * @param ids 公司 ID 集合
   * @return 公司列表
   */
  List<CompanyDO> listByIds(Collection<String> ids);

  /**
   * 保存公司（插入）。
   *
   * @param entity 公司实体
   * @return 插入影响的行数
   */
  int insert(CompanyDO entity);

  /**
   * 更新公司。
   *
   * @param entity 公司实体
   * @return 更新影响的行数
   */
  int updateById(CompanyDO entity);

  /**
   * 删除公司（逻辑删除）。
   *
   * @param id 公司 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 统计符合条件的公司数量。
   *
   * @param wrapper 查询条件
   * @return 公司数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanyDO> wrapper);
}

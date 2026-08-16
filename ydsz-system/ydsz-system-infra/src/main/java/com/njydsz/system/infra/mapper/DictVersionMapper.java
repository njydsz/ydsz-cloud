package com.njydsz.system.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.system.domain.entity.DictVersion;

/**
 * 字典版本管理 Mapper
 *
 * <p>对应数据表 <code>ydsz_dict_version</code>。
 *
 * <p>字典变更（增删改项）生成新版本，支持回滚、对比、灰度发布，避免脏数据扩散。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_type_version — (字典类型+版本号) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 * @see com.njydsz.system.server.service.DictVersionService 字典版本 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface DictVersionMapper extends BaseMapper<DictVersion> {

  /**
   * 按类型编码查询版本历史（按生效时间倒序）
   *
   * <p>走 {@code idx_type_code_version} 复合索引；返回该 typeCode 下所有有效版本（{@code deleted=0}）， 最新版本排首位。
   *
   * @param typeCode 字典类型编码
   * @return 版本列表（按 {@code effective_date} 倒序）
   */
  @Select(
      "SELECT * FROM ydsz_dict_version WHERE type_code = #{typeCode} AND deleted = 0 "
          + "ORDER BY effective_date DESC")
  List<DictVersion> listByTypeCode(@Param("typeCode") String typeCode);
}

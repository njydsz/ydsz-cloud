package com.njydsz.system.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.system.domain.entity.ApiPermission;


/**
 * 接口权限 Mapper
 *
 * <p>对应数据表 <code>ydsz_sys_api_permission</code>。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_ydsz_sys_api_permission_tenant_api — (tenant_id, api_code) 唯一索引
 *   <li>idx_ydsz_sys_api_permission_tenant_deleted — 租户隔离索引
 *   <li>idx_ydsz_sys_api_permission_api_code — 权限码索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段由 MP @TableLogic 自动处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ApiPermission 接口权限实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface ApiPermissionMapper extends BaseMapper<ApiPermission> {

  /**
   * 批量插入接口权限（通过 XML 实现，跳过已有唯一键冲突的项）。
   *
   * <p>使用 PostgreSQL {@code ON CONFLICT (tenant_id, api_code) DO NOTHING} 语法，
   * 保证幂等注册：重复扫描时不覆盖已有记录。
   *
   * @param items 接口权限实体列表
   * @return 实际插入的记录数
   */
  int insertBatchSkipExisting(@Param("items") List<ApiPermission> items);
}

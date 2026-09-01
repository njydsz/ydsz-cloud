package com.njydsz.system.infra.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.system.infra.entity.AppInfo;



/**
 * 应用信息 Mapper
 *
 * <p>对应数据表 <code>ydsz_sys_app_info</code>。
 *
 * <p>应用是系统接入的子系统（业务模块/三方系统），AppId/Secret 用于 API 网关鉴权。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_app_id — AppId 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AppInfo 应用实体
 * @see com.njydsz.system.server.service.AppInfoService 应用 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface AppInfoMapper extends BaseMapper<AppInfo> {

  /**
   * 按应用 Key 查询启用的应用信息（含 appSecret 用于密钥校验）
   *
   * <p>仅返回 {@code status=ENABLED AND deleted=0} 的记录；返回的 DO 包含 {@code appSecret} 字段， <b>仅供</b>
   * {@code AppInfoService.validateClient} 内部 BCrypt 校验使用，<b>禁止</b>透传到 VO。
   *
   * @param appKey 应用 Key
   * @return 应用 DO（含 appSecret）；不存在返回 {@code null}
   */
  @Select(
      "SELECT * FROM ydsz_sys_app_info WHERE app_key = #{appKey} AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
  AppInfo selectEnabledByAppKey(@Param("appKey") String appKey);
}

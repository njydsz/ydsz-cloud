package com.njydsz.system.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.AppInfo;

/**
 * 应用注册 Mapper 接口
 *
 * <p>提供对 {@code ydsz_app_info} 表的 CRUD 操作 + 高频查询自定义 SQL。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力；
 * 通过 {@link Select} 注解声明按 {@code appKey} 单条查询方法。
 *
 * <p><b>自定义 SQL：</b>
 * <ul>
 *   <li>{@link #selectEnabledByAppKey} — 按 {@code appKey} 单条查询（已过滤启用 + 未删除），
 *       <b>返回包含 appSecret 字段</b>，仅供 {@code AppInfoService.validateClient} 内部使用</li>
 * </ul>
 *
 * <p><b>租户隔离：</b>所有查询自动由 MyBatis 拦截器注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>逻辑删除：</b>实体配置了 {@code @TableLogic} 字段 {@code deleted}，删除为逻辑删除。
 *
 * <p><b>安全约束：</b>{@link #selectEnabledByAppKey} 返回的 DO <b>包含</b> {@code appSecret} 字段，
 * 调用方必须限制为内部 Service，且<b>禁止</b>透传到 VO 或前端响应。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.AppInfo 应用注册实体
 * @see com.njydsz.system.server.service.AppInfoService 应用注册 Service
 */
@Mapper
public interface AppInfoMapper extends BaseMapper<AppInfo> {

    /**
     * 按应用 Key 查询启用的应用信息（含 appSecret 用于密钥校验）
     *
     * <p>仅返回 {@code status=ENABLED AND deleted=0} 的记录；返回的 DO 包含 {@code appSecret} 字段，
     * <b>仅供</b> {@code AppInfoService.validateClient} 内部 BCrypt 校验使用，<b>禁止</b>透传到 VO。
     *
     * @param appKey 应用 Key
     * @return 应用 DO（含 appSecret）；不存在返回 {@code null}
     */
    @Select("SELECT * FROM ydsz_app_info WHERE app_key = #{appKey} AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
    AppInfo selectEnabledByAppKey(@Param("appKey") String appKey);
}

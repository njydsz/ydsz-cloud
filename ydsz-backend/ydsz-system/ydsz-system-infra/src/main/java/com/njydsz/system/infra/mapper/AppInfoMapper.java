package com.njydsz.system.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.AppInfo;

/**
 * 应用注册 Mapper。
 *
 * @author ydsz-team
 */
@Mapper
public interface AppInfoMapper extends BaseMapper<AppInfo> {

    /**
     * 按应用 Key 查询启用的应用信息（含 app_secret 用于密钥校验）。
     *
     * @param appKey 应用 Key
     * @return 应用 DO，不存在返回 null
     */
    @Select("SELECT * FROM ydsz_app_info WHERE app_key = #{appKey} AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
    AppInfo selectEnabledByAppKey(@Param("appKey") String appKey);
}

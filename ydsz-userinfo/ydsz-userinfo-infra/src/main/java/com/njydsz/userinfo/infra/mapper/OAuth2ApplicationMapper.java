package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.OAuth2Application;

/**
 * OAuth2 应用 Mapper 接口。
 *
 * <p>对应数据表 {@code ydsz_idp_oauth2_application}，提供 OAuth2 应用记录的 CRUD 操作。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface OAuth2ApplicationMapper extends BaseMapper<OAuth2Application> {}

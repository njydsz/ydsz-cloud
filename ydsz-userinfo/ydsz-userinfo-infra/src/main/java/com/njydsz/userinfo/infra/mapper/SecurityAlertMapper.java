package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.SecurityAlert;

/**
 * 安全告警 Mapper 接口。
 *
 * <p>对应数据表 {@code ydsz_idp_security_alert}，提供安全告警记录的 CRUD 操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface SecurityAlertMapper extends BaseMapper<SecurityAlert> {}

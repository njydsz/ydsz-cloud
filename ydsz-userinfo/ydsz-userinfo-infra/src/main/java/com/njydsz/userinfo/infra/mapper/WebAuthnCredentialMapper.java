package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.domain.entity.WebAuthnCredential;

/**
 * WebAuthn 凭证 Mapper
 *
 * <p>MyBatis-Plus Mapper 接口，提供 CRUD 操作。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface WebAuthnCredentialMapper extends BaseMapper<WebAuthnCredential> {
  // 继承 BaseMapper 提供的标准 CRUD 操作
  // 自定义 SQL 可在此接口中声明
}

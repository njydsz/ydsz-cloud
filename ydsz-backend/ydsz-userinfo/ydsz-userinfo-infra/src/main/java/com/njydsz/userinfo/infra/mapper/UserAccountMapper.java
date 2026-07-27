package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账号 Mapper 接口。
 *
 * <p>对应数据表 ydsz_user_account，
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}

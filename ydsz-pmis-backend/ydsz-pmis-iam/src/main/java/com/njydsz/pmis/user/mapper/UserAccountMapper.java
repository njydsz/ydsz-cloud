package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.UserAccountDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账号 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountDO> {
}

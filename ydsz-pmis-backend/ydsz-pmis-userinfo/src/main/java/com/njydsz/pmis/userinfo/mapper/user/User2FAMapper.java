package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.user.User2FADO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户双因素认证 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface User2FAMapper extends BaseMapper<User2FADO> {

    /**
     * 根据用户 ID 查询双因素认证记录
     *
     * @param userId 用户 ID
     * @return 双因素认证记录，未找到返回 null
     */
    User2FADO selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户 ID 禁用双因素认证
     *
     * @param userId 用户 ID
     * @return 受影响行数
     */
    int disableByUserId(@Param("userId") String userId);
}

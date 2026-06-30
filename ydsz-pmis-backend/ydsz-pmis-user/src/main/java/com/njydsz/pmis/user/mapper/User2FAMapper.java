package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.User2FADO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface User2FAMapper extends BaseMapper<User2FADO> {

    User2FADO selectByUserId(@Param("userId") Long userId);

    int disableByUserId(@Param("userId") Long userId);
}

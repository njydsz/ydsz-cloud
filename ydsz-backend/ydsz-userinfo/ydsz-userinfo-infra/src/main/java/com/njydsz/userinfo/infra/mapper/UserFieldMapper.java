package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.UserFieldDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFieldMapper extends BaseMapper<UserFieldDO> {
}

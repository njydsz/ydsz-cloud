package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.ResourcePoolDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResourcePoolMapper extends BaseMapper<ResourcePoolDO> {

    ResourcePoolDO selectByCode(@Param("code") String code);

    List<ResourcePoolDO> selectByType(@Param("poolType") String poolType);

    List<ResourcePoolDO> selectByDept(@Param("departmentId") Long departmentId);
}

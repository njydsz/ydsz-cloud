package com.njydsz.userinfo.infra.mapper.resource;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.resource.ResourcePoolDO;

/**
 * 资源池 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface ResourcePoolMapper extends BaseMapper<ResourcePoolDO> {

    /**
     * 根据资源池编码查询资源池
     *
     * @param code 资源池编码
     * @return 资源池对象，未找到返回 null
     */
    ResourcePoolDO selectByCode(@Param("code") String code);

    /**
     * 根据资源池类型查询资源池列表
     *
     * @param poolType 资源池类型（HQ/DIVISION/RESERVE）
     * @return 资源池列表
     */
    List<ResourcePoolDO> selectByType(@Param("poolType") String poolType);

    /**
     * 根据部门 ID 查询其下资源池列表
     *
     * @param departmentId 部门 ID
     * @return 资源池列表
     */
    List<ResourcePoolDO> selectByDept(@Param("departmentId") String departmentId);
}

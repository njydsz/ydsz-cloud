package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.PositionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface PositionMapper extends BaseMapper<PositionDO> {

    /**
     * 按部门 ID 查询岗位列表
     *
     * @param departmentId 部门 ID
     * @return 岗位列表
     */
    List<PositionDO> selectByDepartment(@Param("departmentId") String departmentId);

    /**
     * 按岗位编码查询
     *
     * @param positionCode 岗位编码
     * @return 岗位实体，未找到返回 null
     */
    PositionDO selectByCode(@Param("positionCode") String positionCode);

    /**
     * 按职级查询岗位列表
     *
     * @param levelCode 职级编码
     * @return 岗位列表
     */
    List<PositionDO> selectByLevel(@Param("levelCode") String levelCode);
}

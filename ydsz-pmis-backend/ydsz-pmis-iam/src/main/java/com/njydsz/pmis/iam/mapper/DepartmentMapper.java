package com.njydsz.pmis.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.iam.entity.DepartmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentDO> {

    /**
     * 查询所有部门（构建树）
     *
     * @return 部门列表
     */
    @Select("SELECT * FROM pmis_department WHERE deleted = 0 ORDER BY sort_order, id")
    List<DepartmentDO> selectAllEnabled();

    /**
     * 查询某部门的直接子部门
     *
     * @param parentId 父部门 ID
     * @return 子部门列表
     */
    @Select("SELECT * FROM pmis_department WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort_order, id")
    List<DepartmentDO> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 根据 deptCode 查部门
     *
     * @param code 部门编码
     * @return 部门对象，未找到返回 null
     */
    @Select("SELECT * FROM pmis_department WHERE dept_code = #{code} AND deleted = 0 LIMIT 1")
    DepartmentDO selectByCode(@Param("code") String code);
}

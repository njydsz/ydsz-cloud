package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.user.EmployeeTagDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 员工标签 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface EmployeeTagMapper extends BaseMapper<EmployeeTagDO> {

    /**
     * 查询某员工的所有标签
     *
     * @param employeeId 员工 ID
     * @return 标签列表
     */
    List<EmployeeTagDO> selectByEmployee(@Param("employeeId") String employeeId);

    /**
     * 根据标签类型与编码查询被打标的员工标签
     *
     * @param tagType 标签类型
     * @param tagCode 标签编码
     * @return 标签列表
     */
    List<EmployeeTagDO> selectByTag(@Param("tagType") String tagType, @Param("tagCode") String tagCode);

    /**
     * 删除某员工的全部标签
     *
     * @param employeeId 员工 ID
     */
    void deleteByEmployee(@Param("employeeId") String employeeId);
}

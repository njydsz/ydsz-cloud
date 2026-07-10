package com.njydsz.pmis.userinfo.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.user.EmployeeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 员工 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeDO> {

    /**
     * 根据员工编码查询（排除已删除）
     *
     * @param empCode 员工编码
     * @return 员工实体，未找到返回 null
     */
    @Select("SELECT * FROM pmis_employee WHERE emp_code = #{empCode} AND deleted = 0 LIMIT 1")
    EmployeeDO selectByEmpCode(@Param("empCode") String empCode);
}

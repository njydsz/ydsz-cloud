package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RateInternalMapper extends BaseMapper<RateInternalDO> {

    RateInternalDO selectByCode(@Param("code") String code);

    RateInternalDO matchEffective(@Param("levelCode") String levelCode,
                                  @Param("departmentId") Long departmentId,
                                  @Param("date") LocalDate date);

    List<RateInternalDO> selectByLevelAndDept(@Param("levelCode") String levelCode,
                                              @Param("departmentId") Long departmentId);

    /** 全量查询 */
    List<RateInternalDO> selectAll();
}

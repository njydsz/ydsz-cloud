package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.JobLevelDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface JobLevelMapper extends BaseMapper<JobLevelDO> {

    @Select("SELECT * FROM pmis_job_level WHERE level_code = #{code} AND deleted = 0 LIMIT 1")
    JobLevelDO selectByCode(@Param("code") String code);

    @Select("SELECT * FROM pmis_job_level WHERE deleted = 0 ORDER BY sort_order, id")
    List<JobLevelDO> selectAllEnabled();
}

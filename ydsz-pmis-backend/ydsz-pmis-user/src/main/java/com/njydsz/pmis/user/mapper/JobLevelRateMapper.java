package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.JobLevelRateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 职级费率 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobLevelRateMapper extends BaseMapper<JobLevelRateDO> {

    /**
     * 查询某职级当前生效的费率
     *
     * @param code 职级编码
     * @param date 生效日期
     * @return 生效费率记录，未找到返回 null
     */
    @Select("""
            SELECT * FROM pmis_job_level_rate
            WHERE level_code = #{code}
              AND effective_date <= #{date}
              AND (expire_date IS NULL OR expire_date >= #{date})
              AND deleted = 0
            ORDER BY version DESC
            LIMIT 1
            """)
    JobLevelRateDO selectEffective(@Param("code") String code, @Param("date") LocalDate date);

    /**
     * 查询某职级的所有版本
     *
     * @param code 职级编码
     * @return 费率版本列表（按版本号倒序）
     */
    @Select("SELECT * FROM pmis_job_level_rate WHERE level_code = #{code} AND deleted = 0 ORDER BY version DESC")
    List<JobLevelRateDO> selectAllVersions(@Param("code") String code);
}

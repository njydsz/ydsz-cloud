package com.njydsz.pmis.user.service;

import com.njydsz.pmis.user.entity.JobLevelDO;
import com.njydsz.pmis.user.entity.JobLevelRateDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 职级费率服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobLevelService {

    /**
     * 所有职级
     */
    List<JobLevelDO> listAllLevels();

    /**
     * 查询某职级当前生效的费率
     */
    JobLevelRateDO getEffectiveRate(String levelCode, LocalDate date);

    /**
     * 查询某职级所有版本
     */
    List<JobLevelRateDO> listAllVersions(String levelCode);
}

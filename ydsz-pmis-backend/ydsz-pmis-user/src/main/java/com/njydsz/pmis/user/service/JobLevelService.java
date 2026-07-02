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
     *
     * @return 职级列表
     */
    List<JobLevelDO> listAllLevels();

    /**
     * 查询某职级当前生效的费率
     *
     * @param levelCode 职级编码
     * @param date      生效日期
     * @return 生效费率，不存在时返回 null
     */
    JobLevelRateDO getEffectiveRate(String levelCode, LocalDate date);

    /**
     * 查询某职级所有版本
     *
     * @param levelCode 职级编码
     * @return 费率版本列表
     */
    List<JobLevelRateDO> listAllVersions(String levelCode);
}

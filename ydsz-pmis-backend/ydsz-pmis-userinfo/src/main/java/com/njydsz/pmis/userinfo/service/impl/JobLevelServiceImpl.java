package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.entity.JobLevelDO;
import com.njydsz.pmis.userinfo.entity.JobLevelRateDO;
import com.njydsz.pmis.userinfo.mapper.JobLevelMapper;
import com.njydsz.pmis.userinfo.mapper.JobLevelRateMapper;
import com.njydsz.pmis.userinfo.service.JobLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 职级费率服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class JobLevelServiceImpl implements JobLevelService {

    private final JobLevelMapper jobLevelMapper;
    private final JobLevelRateMapper jobLevelRateMapper;

    @Override
    public List<JobLevelDO> listAllLevels() {
        return jobLevelMapper.selectAllEnabled();
    }

    @Override
    public JobLevelRateDO getEffectiveRate(String levelCode, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        JobLevelRateDO rate = jobLevelRateMapper.selectEffective(levelCode, date);
        if (rate == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_c23b2b34" + levelCode);
        }
        return rate;
    }

    @Override
    public List<JobLevelRateDO> listAllVersions(String levelCode) {
        return jobLevelRateMapper.selectAllVersions(levelCode);
    }
}
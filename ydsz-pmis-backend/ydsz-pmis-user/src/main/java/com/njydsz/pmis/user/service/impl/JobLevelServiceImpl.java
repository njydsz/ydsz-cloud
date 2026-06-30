package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.entity.JobLevelDO;
import com.njydsz.pmis.user.entity.JobLevelRateDO;
import com.njydsz.pmis.user.mapper.JobLevelMapper;
import com.njydsz.pmis.user.mapper.JobLevelRateMapper;
import com.njydsz.pmis.user.service.JobLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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
            throw new BizException(BizErrorCode.NOT_FOUND, "职级费率不存在: " + levelCode);
        }
        return rate;
    }

    @Override
    public List<JobLevelRateDO> listAllVersions(String levelCode) {
        return jobLevelRateMapper.selectAllVersions(levelCode);
    }
}

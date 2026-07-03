package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.entity.JobLevelDO;
import com.njydsz.pmis.userinfo.entity.JobLevelRateDO;
import com.njydsz.pmis.userinfo.mapper.JobLevelMapper;
import com.njydsz.pmis.userinfo.mapper.JobLevelRateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("职级费率服务测试")
class JobLevelServiceImplTest {

    @Mock
    private JobLevelMapper jobLevelMapper;
    @Mock
    private JobLevelRateMapper jobLevelRateMapper;

    @InjectMocks
    private JobLevelServiceImpl jobLevelService;

    @Test
    @DisplayName("查询所有职级")
    void listAllLevels_shouldReturnLevelList() {
        JobLevelDO level = new JobLevelDO();
        level.setId(1L);
        level.setLevelCode("L1");
        level.setLevelName("初级");
        level.setStatus("ENABLED");

        when(jobLevelMapper.selectAllEnabled()).thenReturn(List.of(level));

        List<JobLevelDO> result = jobLevelService.listAllLevels();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("L1", result.get(0).getLevelCode());
    }

    @Test
    @DisplayName("查询所有职级为空时返回空列表")
    void listAllLevels_empty_shouldReturnEmptyList() {
        when(jobLevelMapper.selectAllEnabled()).thenReturn(List.of());

        List<JobLevelDO> result = jobLevelService.listAllLevels();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("根据职级编码和日期查询有效费率")
    void getEffectiveRate_shouldReturnRate() {
        JobLevelRateDO rate = new JobLevelRateDO();
        rate.setId(1L);
        rate.setLevelCode("L1");
        rate.setExternalDaily(new BigDecimal("800.00"));
        rate.setInternalDaily(new BigDecimal("500.00"));
        rate.setEffectiveDate(LocalDate.of(2025, 1, 1));

        when(jobLevelRateMapper.selectEffective(eq("L1"), any(LocalDate.class))).thenReturn(rate);

        JobLevelRateDO result = jobLevelService.getEffectiveRate("L1", LocalDate.now());
        assertNotNull(result);
        assertEquals("L1", result.getLevelCode());
        assertEquals(new BigDecimal("800.00"), result.getExternalDaily());
    }

    @Test
    @DisplayName("查询不存在的职级费率时抛出异常")
    void getEffectiveRate_notFound_shouldThrowException() {
        when(jobLevelRateMapper.selectEffective(eq("L99"), any(LocalDate.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> jobLevelService.getEffectiveRate("L99", null));
        assertEquals(10101, ex.getCode());
    }

    @Test
    @DisplayName("查询职级的所有版本费率")
    void listAllVersions_shouldReturnVersionList() {
        JobLevelRateDO rate1 = new JobLevelRateDO();
        rate1.setId(1L);
        rate1.setLevelCode("L1");
        rate1.setVersion(1);

        JobLevelRateDO rate2 = new JobLevelRateDO();
        rate2.setId(2L);
        rate2.setLevelCode("L1");
        rate2.setVersion(2);

        when(jobLevelRateMapper.selectAllVersions("L1")).thenReturn(List.of(rate1, rate2));

        List<JobLevelRateDO> result = jobLevelService.listAllVersions("L1");
        assertNotNull(result);
        assertEquals(2, result.size());
    }
}
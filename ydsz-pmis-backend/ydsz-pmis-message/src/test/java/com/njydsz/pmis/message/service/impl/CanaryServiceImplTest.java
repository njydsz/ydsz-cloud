package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.canary.CanaryUpsertDTO;
import com.njydsz.pmis.message.entity.canary.MsgCanaryDO;
import com.njydsz.pmis.message.mapper.canary.MsgCanaryMapper;
import com.njydsz.pmis.message.service.impl.canary.CanaryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 金丝雀灰度桶服务单元测试。
 *
 * <p>覆盖 upsert 新建/更新、百分比灰度命中判定、桶列表构造、边界条件。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("灰度服务 CanaryServiceImpl 单元测试")
class CanaryServiceImplTest {

    @Mock
    private MsgCanaryMapper msgCanaryMapper;

    @InjectMocks
    private CanaryServiceImpl canaryService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== upsert ====================

    @Test
    @DisplayName("正常场景：新建灰度桶")
    void 新建灰度桶() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(50);
        dto.setExperimentTemplateCode("TPL_001_EXP");
        dto.setExperimentChannel("EMAIL");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertNotNull(result);
        assertEquals("TPL_001", result.getCanaryKey());
        assertEquals(50, result.getPercentage());
        assertEquals(100, result.getBucketTotal());
        assertEquals("TPL_001_EXP", result.getExperimentTemplateCode());
        assertEquals("EMAIL", result.getExperimentChannel());
        assertEquals("ENABLED", result.getStatus());
        // 验证 bucketSelected 为 [0,1,2,...,49] 的 JSON
        assertEquals("[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49]",
                result.getBucketSelected());
        verify(msgCanaryMapper).insert(result);
    }

    @Test
    @DisplayName("正常场景：更新已有灰度桶")
    void 更新已有灰度桶() {
        MsgCanaryDO existing = new MsgCanaryDO();
        existing.setId("1");
        existing.setCanaryKey("TPL_001");
        existing.setPercentage(10);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(80);
        dto.setExperimentTemplateCode("TPL_001_V2");

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals(80, result.getPercentage());
        assertEquals("TPL_001_V2", result.getExperimentTemplateCode());
        verify(msgCanaryMapper).updateById(existing);
        verify(msgCanaryMapper, never()).insert(any(MsgCanaryDO.class));
    }

    @Test
    @DisplayName("边界场景：percentage 超出 100 时截断为 100")
    void percentage超出100截断() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(150);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals(100, result.getPercentage());
    }

    @Test
    @DisplayName("边界场景：percentage 为负数时截断为 0")
    void percentage为负数截断为0() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(-10);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals(0, result.getPercentage());
    }

    @Test
    @DisplayName("边界场景：percentage 为 null 时默认为 0")
    void percentage为null默认0() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(null);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals(0, result.getPercentage());
    }

    @Test
    @DisplayName("边界场景：bucketTotal 为 null 时默认 100")
    void bucketTotal为null默认100() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(10);
        dto.setBucketTotal(null);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals(100, result.getBucketTotal());
    }

    @Test
    @DisplayName("边界场景：bucketTotal <= 0 时默认 100")
    void bucketTotal为零默认100() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(10);
        dto.setBucketTotal(0);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals(100, result.getBucketTotal());
    }

    @Test
    @DisplayName("边界场景：status 为空时默认 ENABLED")
    void status为空默认ENABLED() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_001");
        dto.setPercentage(10);
        dto.setStatus(null);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertEquals("ENABLED", result.getStatus());
    }

    @Test
    @DisplayName("异常场景：dto 为空抛 BizException")
    void upsertDto为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> canaryService.upsert(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：canaryKey 为空抛 BizException")
    void upsertCanaryKey为空抛异常() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setPercentage(10);

        BizException ex = assertThrows(BizException.class, () -> canaryService.upsert(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== matchConfig / hit ====================

    @Test
    @DisplayName("正常场景：灰度配置存在且 bucket < percentage 时命中")
    void 灰度命中() {
        MsgCanaryDO config = new MsgCanaryDO();
        config.setCanaryKey("TPL_001");
        config.setPercentage(100);
        config.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        MsgCanaryDO result = canaryService.matchConfig("TPL_001", "receiver-001");

        assertNotNull(result);
        assertEquals(100, result.getPercentage());
    }

    @Test
    @DisplayName("边界场景：canaryKey 为空返回 null")
    void matchConfigCanaryKey为空返回null() {
        MsgCanaryDO result = canaryService.matchConfig(null, "receiver-001");
        assertNull(result);
    }

    @Test
    @DisplayName("边界场景：bucketValue 为空返回 null")
    void matchConfigBucketValue为空返回null() {
        MsgCanaryDO result = canaryService.matchConfig("TPL_001", null);
        assertNull(result);
    }

    @Test
    @DisplayName("边界场景：percentage <= 0 返回 null")
    void percentage为零返回null() {
        MsgCanaryDO config = new MsgCanaryDO();
        config.setCanaryKey("TPL_001");
        config.setPercentage(0);
        config.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        MsgCanaryDO result = canaryService.matchConfig("TPL_001", "receiver-001");

        assertNull(result);
    }

    @Test
    @DisplayName("边界场景：percentage 为 null 返回 null")
    void percentage为null返回null() {
        MsgCanaryDO config = new MsgCanaryDO();
        config.setCanaryKey("TPL_001");
        config.setPercentage(null);
        config.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        MsgCanaryDO result = canaryService.matchConfig("TPL_001", "receiver-001");

        assertNull(result);
    }

    @Test
    @DisplayName("边界场景：配置不存在返回 null")
    void 配置不存在返回null() {
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.matchConfig("TPL_001", "receiver-001");

        assertNull(result);
    }

    @Test
    @DisplayName("正常场景：hit 命中时返回 true")
    void hit命中返回true() {
        MsgCanaryDO config = new MsgCanaryDO();
        config.setCanaryKey("TPL_001");
        config.setPercentage(100);
        config.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        boolean result = canaryService.hit("TPL_001", "receiver-001");

        assertEquals(true, result);
    }

    @Test
    @DisplayName("正常场景：hit 未命中时返回 false")
    void hit未命中返回false() {
        MsgCanaryDO config = new MsgCanaryDO();
        config.setCanaryKey("TPL_001");
        config.setPercentage(0);
        config.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        boolean result = canaryService.hit("TPL_001", "receiver-001");

        assertEquals(false, result);
    }

    // ==================== getByKey ====================

    @Test
    @DisplayName("正常场景：按 canaryKey 查询")
    void 按canaryKey查询() {
        MsgCanaryDO entity = new MsgCanaryDO();
        entity.setCanaryKey("TPL_001");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MsgCanaryDO result = canaryService.getByKey("TPL_001");

        assertNotNull(result);
        assertEquals("TPL_001", result.getCanaryKey());
    }

    @Test
    @DisplayName("边界场景：canaryKey 为空返回 null")
    void getByKeyCanaryKey为空返回null() {
        MsgCanaryDO result = canaryService.getByKey(null);
        assertNull(result);
    }

    // ==================== page ====================

    @Test
    @DisplayName("正常场景：分页查询灰度桶")
    void 分页查询灰度桶() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        Page<MsgCanaryDO> mockPage = new Page<>();
        when(msgCanaryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgCanaryDO> result = canaryService.page(query);

        assertNotNull(result);
    }
}

package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.dto.CanaryUpsertDTO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;
import com.njydsz.pmis.message.mapper.MsgCanaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CanaryServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CanaryServiceImpl 灰度桶测试")
@ExtendWith(MockitoExtension.class)
class CanaryServiceImplTest {

    @Mock
    private MsgCanaryMapper msgCanaryMapper;

    @InjectMocks
    private CanaryServiceImpl canaryService;

    @Test
    @DisplayName("upsert 不存在时新建并重算 bucketSelected")
    void upsertShouldInsertAndComputeBuckets() {
        CanaryUpsertDTO dto = new CanaryUpsertDTO();
        dto.setCanaryKey("TPL_X");
        dto.setPercentage(30);
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgCanaryDO result = canaryService.upsert(dto);

        assertTrue(result.getPercentage() == 30);
        assertTrue(result.getBucketSelected().contains("0"));
        verify(msgCanaryMapper).insert(any(MsgCanaryDO.class));
    }

    @Test
    @DisplayName("hit 百分比为 0 时永不命中")
    void hitShouldReturnFalseWhenPercentageZero() {
        MsgCanaryDO cfg = new MsgCanaryDO();
        cfg.setCanaryKey("TPL_X");
        cfg.setPercentage(0);
        cfg.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cfg);

        assertFalse(canaryService.hit("TPL_X", "receiver-1"));
    }

    @Test
    @DisplayName("hit 百分比为 100 时总命中")
    void hitShouldReturnTrueWhenPercentage100() {
        MsgCanaryDO cfg = new MsgCanaryDO();
        cfg.setCanaryKey("TPL_X");
        cfg.setPercentage(100);
        cfg.setStatus("ENABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cfg);

        assertTrue(canaryService.hit("TPL_X", "any-bucket-value"));
    }

    @Test
    @DisplayName("hit DISABLED 状态不命中")
    void hitShouldReturnFalseWhenDisabled() {
        MsgCanaryDO cfg = new MsgCanaryDO();
        cfg.setCanaryKey("TPL_X");
        cfg.setPercentage(50);
        cfg.setStatus("DISABLED");
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cfg);

        assertFalse(canaryService.hit("TPL_X", "any"));
    }

    @Test
    @DisplayName("hit 无配置时返回 false")
    void hitShouldReturnFalseWhenNoConfig() {
        when(msgCanaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(canaryService.hit("TPL_X", "any"));
    }
}

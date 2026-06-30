package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.OpportunityFollowDTO;
import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.entity.OpportunityFollowDO;
import com.njydsz.pmis.project.mapper.OpportunityFollowMapper;
import com.njydsz.pmis.project.mapper.OpportunityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OpportunityFollowServiceImpl 跟进服务测试")
class OpportunityFollowServiceImplTest {

    private OpportunityFollowMapper followMapper;
    private OpportunityMapper oppMapper;
    private OpportunityFollowServiceImpl service;

    @BeforeEach
    void setUp() {
        followMapper = mock(OpportunityFollowMapper.class);
        oppMapper = mock(OpportunityMapper.class);
        service = new OpportunityFollowServiceImpl(followMapper, oppMapper);
    }

    @Test
    @DisplayName("记录 - 商机不存在抛 NOT_FOUND")
    void oppMissing() {
        when(oppMapper.selectById(1L)).thenReturn(null);
        OpportunityFollowDTO dto = new OpportunityFollowDTO();
        dto.setOpportunityId(1L);
        dto.setFollowType("VISIT");
        assertThatThrownBy(() -> service.record(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10101);
    }

    @Test
    @DisplayName("记录 - 必填校验")
    void validate() {
        OpportunityFollowDTO dto = new OpportunityFollowDTO();
        assertThatThrownBy(() -> service.record(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("记录成功 - 写入 followAt")
    void recordOk() {
        when(oppMapper.selectById(1L)).thenReturn(new OpportunityDO());
        when(followMapper.insert(any(OpportunityFollowDO.class))).thenAnswer(inv -> {
            OpportunityFollowDO f = inv.getArgument(0);
            f.setId(99L);
            return 1;
        });
        OpportunityFollowDTO dto = new OpportunityFollowDTO();
        dto.setOpportunityId(1L);
        dto.setFollowType("CALL");
        dto.setContent("首次电话沟通");
        Long id = service.record(dto);
        assertThat(id).isEqualTo(99L);

        ArgumentCaptor<OpportunityFollowDO> captor = ArgumentCaptor.forClass(OpportunityFollowDO.class);
        verify(followMapper).insert(captor.capture());
        assertThat(captor.getValue().getFollowAt()).isNotNull();
    }
}

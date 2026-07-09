package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.initiation.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.opportunity.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.opportunity.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.opportunity.OpportunityUpdateDTO;
import com.njydsz.pmis.project.entity.opportunity.OpportunityDO;
import com.njydsz.pmis.project.enums.opportunity.OpportunityStatus;
import com.njydsz.pmis.project.mapper.opportunity.OpportunityMapper;
import com.njydsz.pmis.project.service.initiation.InitiationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商机服务实现单元测试
 *
 * <p>覆盖商机核心写路径：创建、更新、状态迁移、删除、详情、分页、赢率评估、转立项。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityServiceImpl 商机服务测试")
class OpportunityServiceImplTest {

    @Mock
    private OpportunityMapper opportunityMapper;
    @Mock
    private NameAssembler nameAssembler;
    @Mock
    private InitiationService initiationService;

    @InjectMocks
    private OpportunityServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("T001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OpportunityCreateDTO buildCreateDto() {
        OpportunityCreateDTO dto = new OpportunityCreateDTO();
        dto.setOpportunityCode("OPP-202607090001");
        dto.setOpportunityName("测试商机");
        dto.setCustomerId("C001");
        dto.setOwnerId("U001");
        dto.setEstimatedAmount(new BigDecimal("100000"));
        return dto;
    }

    private OpportunityDO buildOpportunity(String id, String status) {
        OpportunityDO o = new OpportunityDO();
        o.setId(id);
        o.setOpportunityCode("OPP-202607090001");
        o.setOpportunityName("测试商机");
        o.setCustomerId("C001");
        o.setOwnerId("U001");
        o.setStatus(status);
        o.setLevel("B");
        o.setEstimatedAmount(new BigDecimal("100000"));
        o.setTenantId("T001");
        return o;
    }

    @Test
    @DisplayName("创建商机：参数合法且编号唯一，返回主键并装配默认状态/级别/租户/赢率")
    void create_success() {
        OpportunityCreateDTO dto = buildCreateDto();
        when(opportunityMapper.selectByCode(dto.getOpportunityCode())).thenReturn(null);
        when(nameAssembler.resolveCustomer("C001")).thenReturn("测试客户");
        when(nameAssembler.resolveEmployee("U001")).thenReturn("测试负责人");

        AtomicReference<OpportunityDO> savedRef = new AtomicReference<>();
        doAnswer(inv -> {
            OpportunityDO saved = inv.getArgument(0);
            saved.setId("OPP-ID-1");
            savedRef.set(saved);
            return 1;
        }).when(opportunityMapper).insert(any(OpportunityDO.class));

        String id = service.create(dto);

        assertThat(id).isNotBlank();
        OpportunityDO saved = savedRef.get();
        assertThat(saved).isNotNull();
        assertThat(saved.getOpportunityCode()).isEqualTo(dto.getOpportunityCode());
        assertThat(saved.getStatus()).isEqualTo(OpportunityStatus.FOLLOWING.getCode());
        assertThat(saved.getLevel()).isEqualTo("C");
        assertThat(saved.getTenantId()).isEqualTo("T001");
        assertThat(saved.getWinRate()).isNotNull();
        assertThat(saved.getCustomerName()).isEqualTo("测试客户");
        assertThat(saved.getOwnerName()).isEqualTo("测试负责人");
    }

    @Test
    @DisplayName("创建商机：编号重复时抛 DUPLICATE_KEY 异常")
    void create_duplicateCode_throws() {
        OpportunityCreateDTO dto = buildCreateDto();
        when(opportunityMapper.selectByCode(dto.getOpportunityCode())).thenReturn(new OpportunityDO());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException be = (BizException) e;
                    assertThat(be.getCode()).isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
                });
        verify(opportunityMapper, never()).insert(any(OpportunityDO.class));
    }

    @Test
    @DisplayName("创建商机：必填字段缺失时抛 BAD_REQUEST 异常")
    void create_invalidDto_throws() {
        OpportunityCreateDTO dto = new OpportunityCreateDTO();
        dto.setOpportunityCode("OPP-001");
        // 缺少名称/客户/负责人

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("更新商机：按非空字段覆盖并持久化")
    void update_success() {
        String id = "OPP-1";
        OpportunityDO existing = buildOpportunity(id, OpportunityStatus.FOLLOWING.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(existing);

        OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
        dto.setId(id);
        dto.setOpportunityName("更新后的商机名称");
        dto.setLevel("A");
        dto.setIndustry("IT");
        dto.setEstimatedAmount(new BigDecimal("200000"));
        dto.setWinRate(new BigDecimal("0.75"));
        dto.setExpectedSignDate(LocalDate.of(2026, 12, 31));
        dto.setCompetitor("竞对A");
        dto.setRemark("备注更新");
        dto.setTags("tag1,tag2");

        service.update(dto);

        verify(opportunityMapper).updateById(existing);
        assertThat(existing.getOpportunityName()).isEqualTo("更新后的商机名称");
        assertThat(existing.getLevel()).isEqualTo("A");
        assertThat(existing.getIndustry()).isEqualTo("IT");
        assertThat(existing.getEstimatedAmount()).isEqualTo(new BigDecimal("200000"));
        assertThat(existing.getWinRate()).isEqualTo(new BigDecimal("0.75"));
        assertThat(existing.getExpectedSignDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(existing.getCompetitor()).isEqualTo("竞对A");
    }

    @Test
    @DisplayName("更新商机：ID 为空时抛 BAD_REQUEST 异常")
    void update_nullId_throws() {
        OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
        dto.setOpportunityName("名称");

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("更新商机：商机不存在时抛 NOT_FOUND 异常")
    void update_notFound_throws() {
        String id = "OPP-404";
        when(opportunityMapper.selectById(id)).thenReturn(null);

        OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
        dto.setId(id);
        dto.setOpportunityName("名称");

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("状态迁移：FOLLOWING -> QUOTED 成功")
    void changeStatus_followingToQuoted_success() {
        String id = "OPP-1";
        OpportunityDO o = buildOpportunity(id, OpportunityStatus.FOLLOWING.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(o);

        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(id);
        dto.setTargetStatus(OpportunityStatus.QUOTED.getCode());

        service.changeStatus(dto);

        verify(opportunityMapper).updateStatus(id, OpportunityStatus.QUOTED.getCode(), null);
    }

    @Test
    @DisplayName("状态迁移：非法迁移 FOLLOWING -> CONVERTED 抛异常")
    void changeStatus_invalidTransition_throws() {
        String id = "OPP-1";
        OpportunityDO o = buildOpportunity(id, OpportunityStatus.FOLLOWING.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(o);

        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(id);
        dto.setTargetStatus(OpportunityStatus.CONVERTED.getCode());

        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("状态迁移：LOST 缺少输单原因时抛异常")
    void changeStatus_lostWithoutReason_throws() {
        String id = "OPP-1";
        OpportunityDO o = buildOpportunity(id, OpportunityStatus.NEGOTIATING.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(o);

        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(id);
        dto.setTargetStatus(OpportunityStatus.LOST.getCode());

        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("删除商机：按主键删除")
    void delete_success() {
        String id = "OPP-1";
        OpportunityDO o = buildOpportunity(id, OpportunityStatus.FOLLOWING.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(o);

        service.delete(id);

        verify(opportunityMapper).deleteById(id);
    }

    @Test
    @DisplayName("查询商机：存在时返回实体")
    void getById_success() {
        String id = "OPP-1";
        OpportunityDO o = buildOpportunity(id, OpportunityStatus.FOLLOWING.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(o);

        OpportunityDO result = service.getById(id);

        assertThat(result).isSameAs(o);
    }

    @Test
    @DisplayName("查询商机：不存在时抛 NOT_FOUND 异常")
    void getById_notFound_throws() {
        String id = "OPP-404";
        when(opportunityMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("分页查询：按关键词/状态/级别/负责人过滤并倒序")
    void page_success() {
        Page<OpportunityDO> page = new Page<>(1, 10);
        when(opportunityMapper.selectPage(any(), any())).thenReturn(page);

        Page<OpportunityDO> result = service.page(1, 10, "测试", "FOLLOWING", "B", "U001");

        assertThat(result).isSameAs(page);
        verify(opportunityMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("评估赢单率：非终态商机按多因子模型计算并回写")
    void evaluateWinRate_success() {
        String id = "OPP-1";
        OpportunityDO o = buildOpportunity(id, OpportunityStatus.QUOTED.getCode());
        when(opportunityMapper.selectById(id)).thenReturn(o);

        BigDecimal rate = service.evaluateWinRate(id, "A", true);

        assertThat(rate).isNotNull();
        verify(opportunityMapper).updateById(o);
        assertThat(o.getWinRate()).isEqualTo(rate);
    }

    @Test
    @DisplayName("转立项：WON 状态商机成功创建立项并推进至 CONVERTED")
    void convertToInitiation_success() {
        String oppId = "OPP-WON";
        OpportunityDO o = buildOpportunity(oppId, OpportunityStatus.WON.getCode());
        when(opportunityMapper.selectById(oppId)).thenReturn(o);

        AtomicReference<InitiationCreateDTO> dtoRef = new AtomicReference<>();
        doAnswer(inv -> {
            dtoRef.set(inv.getArgument(0));
            return "INIT-1";
        }).when(initiationService).create(any(InitiationCreateDTO.class));

        String initiationId = service.convertToInitiation(oppId, "S001", "PM001");

        assertThat(initiationId).isEqualTo("INIT-1");
        InitiationCreateDTO dto = dtoRef.get();
        assertThat(dto).isNotNull();
        assertThat(dto.getOpportunityId()).isEqualTo(oppId);
        assertThat(dto.getCustomerId()).isEqualTo("C001");
        assertThat(dto.getPmId()).isEqualTo("PM001");
        assertThat(dto.getSponsorId()).isEqualTo("S001");
        assertThat(dto.getProjectType()).isEqualTo("OUTSOURCING");
        verify(opportunityMapper).updateStatus(eq(oppId), eq(OpportunityStatus.CONVERTED.getCode()), eq(null));
    }

    @Test
    @DisplayName("转立项：非 WON 状态商机抛异常")
    void convertToInitiation_notWon_throws() {
        String oppId = "OPP-FOLLOWING";
        OpportunityDO o = buildOpportunity(oppId, OpportunityStatus.FOLLOWING.getCode());
        when(opportunityMapper.selectById(oppId)).thenReturn(o);

        assertThatThrownBy(() -> service.convertToInitiation(oppId, "S001", "PM001"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(BizErrorCode.BAD_REQUEST.getCode()));
        verify(initiationService, never()).create(any(InitiationCreateDTO.class));
    }

    @Test
    @DisplayName("按状态聚合：空租户时默认使用当前租户")
    void aggregateByStatus_defaultTenant() {
        service.aggregateByStatus(null);
        verify(opportunityMapper).aggregateByStatus("T001");
    }
}

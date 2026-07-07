package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.dto.TemplateAuditDTO;
import com.njydsz.pmis.message.dto.TemplateCreateDTO;
import com.njydsz.pmis.message.dto.TemplateQueryDTO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.enums.TemplateAuditStatusEnum;
import com.njydsz.pmis.message.mapper.MsgTemplateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TemplateServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TemplateServiceImpl 模板服务测试")
@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock
    private MsgTemplateMapper msgTemplateMapper;

    @InjectMocks
    private TemplateServiceImpl templateService;

    @Test
    @DisplayName("create 校验唯一性,重复抛 BizException")
    void createShouldRejectDuplicate() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setTemplateCode("CODE1");
        dto.setChannel("SMS");
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new MsgTemplateDO());

        BizException ex = assertThrows(BizException.class, () -> templateService.create(dto));
        assertEquals(10102, ex.getCode());
        verify(msgTemplateMapper, never()).insert(any(MsgTemplateDO.class));
    }

    @Test
    @DisplayName("create 唯一时正常插入")
    void createShouldInsertWhenUnique() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setTemplateCode("CODE1");
        dto.setChannel("SMS");
        dto.setContent("hi ${name}");
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgTemplateDO result = templateService.create(dto);

        assertNotNull(result);
        assertEquals(TemplateAuditStatusEnum.DRAFT.name(), result.getAuditStatus());
        verify(msgTemplateMapper, times(1)).insert(any(MsgTemplateDO.class));
    }

    @Test
    @DisplayName("loadByCodeAndChannel 精确 locale 命中时直接返回")
    void loadShouldReturnExactLocale() {
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setLocale("en-US");
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tpl);

        MsgTemplateDO result = templateService.loadByCodeAndChannel("CODE", "SMS", "en-US", "1");
        assertEquals("en-US", result.getLocale());
    }

    @Test
    @DisplayName("loadByCodeAndChannel locale 回退默认 zh-CN")
    void loadShouldFallbackToDefaultLocale() {
        // 第一次精确查询返回 null,第二次回退查询返回模板
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(new MsgTemplateDO());

        MsgTemplateDO result = templateService.loadByCodeAndChannel("CODE", "SMS", "en-US", "1");
        assertNotNull(result);
        verify(msgTemplateMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("audit 状态流转 DRAFT→AUDITING 合法")
    void auditShouldAllowDraftToAuditing() {
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setId("1");
        tpl.setAuditStatus(TemplateAuditStatusEnum.DRAFT.name());
        when(msgTemplateMapper.selectById(anyString())).thenReturn(tpl);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus(TemplateAuditStatusEnum.AUDITING.name());
        templateService.audit("1", dto);

        verify(msgTemplateMapper).updateById(any(MsgTemplateDO.class));
    }

    @Test
    @DisplayName("audit 非法状态流转 APPROVED→AUDITING 抛 BizException")
    void auditShouldRejectInvalidTransition() {
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setId("1");
        tpl.setAuditStatus(TemplateAuditStatusEnum.APPROVED.name());
        when(msgTemplateMapper.selectById(anyString())).thenReturn(tpl);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus(TemplateAuditStatusEnum.AUDITING.name());

        assertThrows(BizException.class, () -> templateService.audit("1", dto));
    }

    @Test
    @DisplayName("page 分页查询调用 selectPage")
    void pageShouldCallSelectPage() {
        TemplateQueryDTO query = new TemplateQueryDTO();
        query.setPage(1);
        query.setSize(10);
        Page<MsgTemplateDO> mockPage = new Page<>();
        when(msgTemplateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgTemplateDO> result = templateService.page(query);
        assertNotNull(result);
    }
}

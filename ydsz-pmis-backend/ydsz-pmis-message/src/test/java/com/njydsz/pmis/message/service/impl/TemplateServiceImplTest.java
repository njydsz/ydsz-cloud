package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.dto.template.TemplateAuditDTO;
import com.njydsz.pmis.message.dto.template.TemplateCreateDTO;
import com.njydsz.pmis.message.dto.template.TemplateQueryDTO;
import com.njydsz.pmis.message.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.enums.template.TemplateAuditStatusEnum;
import com.njydsz.pmis.message.mapper.template.MsgTemplateMapper;
import com.njydsz.pmis.message.template.DefaultTemplateEngine;
import com.njydsz.pmis.message.template.TemplateEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板服务单元测试。
 *
 * <p>覆盖模板 CRUD、locale 回退加载、审核状态流转、以及 {@link TemplateEngine} 的
 * ${var} / ${a.b.c} 嵌套变量替换能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("模板服务 TemplateServiceImpl 单元测试")
class TemplateServiceImplTest {

    @Mock
    private MsgTemplateMapper msgTemplateMapper;

    @InjectMocks
    private TemplateServiceImpl templateService;

    /** 真实模板引擎，用于验证 ${var} 嵌套变量替换 */
    private final TemplateEngine templateEngine = new DefaultTemplateEngine();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 模板引擎 ${var} 嵌套变量替换 ====================

    @Test
    @DisplayName("模板渲染：${var} 简单变量替换")
    void 简单变量替换() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("amount", "100");

        String result = templateEngine.render("您好 ${name}，金额 ${amount} 元", params);

        assertEquals("您好 张三，金额 100 元", result);
    }

    @Test
    @DisplayName("模板渲染：${a.b.c} 嵌套 Map 取值")
    void 嵌套Map取值() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("name", "李四");
        inner.put("city", "南京");
        Map<String, Object> user = new HashMap<>();
        user.put("name", "王五");
        user.put("profile", inner);
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);

        String result = templateEngine.render(
                "用户 ${user.name} 来自 ${user.profile.city}", params);

        assertEquals("用户 王五 来自 南京", result);
    }

    @Test
    @DisplayName("模板渲染：未命中变量替换为空串")
    void 未命中变量替换为空串() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        String result = templateEngine.render("您好 ${name}，金额 ${amount} 元", params);

        assertEquals("您好 张三，金额  元", result);
    }

    @Test
    @DisplayName("模板渲染：params 为 null 时原样返回模板")
    void params为null原样返回() {
        String template = "您好 ${name}";
        String result = templateEngine.render(template, null);
        assertEquals(template, result);
    }

    @Test
    @DisplayName("模板渲染：空模板返回空串")
    void 空模板返回空串() {
        String result = templateEngine.render("", new HashMap<>());
        assertEquals("", result);
    }

    @Test
    @DisplayName("模板渲染：null 模板返回空串")
    void null模板返回空串() {
        String result = templateEngine.render(null, new HashMap<>());
        assertEquals("", result);
    }

    @Test
    @DisplayName("模板渲染：{{#if}} 条件渲染 true 分支")
    void 条件渲染True分支() {
        Map<String, Object> params = new HashMap<>();
        params.put("vip", true);

        String result = templateEngine.render("{{#if vip}}尊享会员{{else}}普通用户{{/if}}", params);

        assertEquals("尊享会员", result);
    }

    @Test
    @DisplayName("模板渲染：{{#if}} 条件渲染 false 分支")
    void 条件渲染False分支() {
        Map<String, Object> params = new HashMap<>();
        params.put("vip", false);

        String result = templateEngine.render("{{#if vip}}尊享会员{{else}}普通用户{{/if}}", params);

        assertEquals("普通用户", result);
    }

    @Test
    @DisplayName("模板渲染：{{#each}} 循环渲染")
    void 循环渲染() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of("苹果", "香蕉", "橙子"));

        String result = templateEngine.render("{{#each items}}- ${this}\n{{/each}}", params);

        assertEquals("- 苹果\n- 香蕉\n- 橙子\n", result);
    }

    @Test
    @DisplayName("模板渲染：必填参数缺失抛 BizException")
    void 必填参数缺失抛异常() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        BizException ex = assertThrows(BizException.class, () ->
                templateEngine.render("您好 ${name} ${amount}", params, java.util.Set.of("amount")));

        assertEquals(BizErrorCode.MISSING_PARAMETER.getCode(), ex.getCode());
    }

    // ==================== create ====================

    @Test
    @DisplayName("正常场景：创建模板成功")
    void 创建模板成功() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setTemplateCode("TPL_001");
        dto.setChannel("SMS");
        dto.setContent("您好 ${name}");
        dto.setSubject("通知");
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgTemplateDO result = templateService.create(dto);

        assertNotNull(result);
        assertEquals("TPL_001", result.getTemplateCode());
        assertEquals("SMS", result.getChannel());
        assertEquals(MessageConstants.DEFAULT_LOCALE, result.getLocale());
        assertEquals("ENABLED", result.getStatus());
        assertEquals(TemplateAuditStatusEnum.DRAFT.name(), result.getAuditStatus());
        verify(msgTemplateMapper).insert(result);
    }

    @Test
    @DisplayName("边界场景：locale 为空时使用默认 zh-CN")
    void locale为空使用默认值() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setTemplateCode("TPL_002");
        dto.setChannel("EMAIL");
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgTemplateDO result = templateService.create(dto);

        assertEquals(MessageConstants.DEFAULT_LOCALE, result.getLocale());
    }

    @Test
    @DisplayName("异常场景：dto 为空抛 BizException")
    void dto为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> templateService.create(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：templateCode 为空抛 BizException")
    void templateCode为空抛异常() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setChannel("SMS");

        BizException ex = assertThrows(BizException.class, () -> templateService.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：channel 为空抛 BizException")
    void channel为空抛异常() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setTemplateCode("TPL_001");

        BizException ex = assertThrows(BizException.class, () -> templateService.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：模板已存在抛 DUPLICATE_KEY")
    void 模板已存在抛异常() {
        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setTemplateCode("TPL_001");
        dto.setChannel("SMS");
        MsgTemplateDO existing = new MsgTemplateDO();
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> templateService.create(dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(msgTemplateMapper, never()).insert(any(MsgTemplateDO.class));
    }

    // ==================== getById / update / delete ====================

    @Test
    @DisplayName("正常场景：按 ID 查询模板")
    void 按ID查询模板() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setTemplateCode("TPL_001");
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        MsgTemplateDO result = templateService.getById("1");

        assertEquals("TPL_001", result.getTemplateCode());
    }

    @Test
    @DisplayName("异常场景：ID 为空抛 BizException")
    void id为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> templateService.getById(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：模板不存在抛 NOT_FOUND")
    void 模板不存在抛异常() {
        when(msgTemplateMapper.selectById("999")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> templateService.getById("999"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常场景：更新模板成功")
    void 更新模板成功() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setTemplateCode("TPL_001");
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        TemplateCreateDTO dto = new TemplateCreateDTO();
        dto.setContent("新内容");
        dto.setSubject("新主题");

        MsgTemplateDO result = templateService.update("1", dto);

        assertEquals("新内容", result.getContent());
        assertEquals("新主题", result.getSubject());
        verify(msgTemplateMapper).updateById(entity);
    }

    @Test
    @DisplayName("异常场景：更新时 ID 为空抛 BizException")
    void 更新时id为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> templateService.update(null, new TemplateCreateDTO()));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常场景：删除模板成功")
    void 删除模板成功() {
        templateService.delete("1");
        verify(msgTemplateMapper).deleteById("1");
    }

    @Test
    @DisplayName("异常场景：删除时 ID 为空抛 BizException")
    void 删除时id为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> templateService.delete(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== loadByCodeAndChannel ====================

    @Test
    @DisplayName("正常场景：精确 locale 命中")
    void 精确locale命中() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setTemplateCode("TPL_001");
        entity.setLocale("en-US");
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MsgTemplateDO result = templateService.loadByCodeAndChannel("TPL_001", "SMS", "en-US", "1");

        assertNotNull(result);
        assertEquals("en-US", result.getLocale());
    }

    @Test
    @DisplayName("回退场景：精确 locale 未命中时回退 zh-CN")
    void 精确locale未命中回退zhCN() {
        MsgTemplateDO fallback = new MsgTemplateDO();
        fallback.setTemplateCode("TPL_001");
        fallback.setLocale(MessageConstants.DEFAULT_LOCALE);
        // 第一次精确查询返回 null，第二次回退查询返回模板
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(fallback);

        MsgTemplateDO result = templateService.loadByCodeAndChannel("TPL_001", "SMS", "en-US", "1");

        assertNotNull(result);
        assertEquals(MessageConstants.DEFAULT_LOCALE, result.getLocale());
    }

    @Test
    @DisplayName("边界场景：locale 为 null 时使用默认 zh-CN（仅查一次）")
    void locale为null使用默认() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setLocale(MessageConstants.DEFAULT_LOCALE);
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MsgTemplateDO result = templateService.loadByCodeAndChannel("TPL_001", "SMS", null, "1");

        assertNotNull(result);
    }

    @Test
    @DisplayName("边界场景：精确与回退均未命中返回 null")
    void 精确与回退均未命中返回null() {
        when(msgTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgTemplateDO result = templateService.loadByCodeAndChannel("TPL_001", "SMS", "en-US", "1");

        assertNull(result);
    }

    @Test
    @DisplayName("异常场景：templateCode 为空抛 BizException")
    void loadByCodeTemplateCode为空抛异常() {
        BizException ex = assertThrows(BizException.class,
                () -> templateService.loadByCodeAndChannel(null, "SMS", "zh-CN", "1"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：channel 为空抛 BizException")
    void loadByCodeChannel为空抛异常() {
        BizException ex = assertThrows(BizException.class,
                () -> templateService.loadByCodeAndChannel("TPL_001", null, "zh-CN", "1"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== audit ====================

    @Test
    @DisplayName("正常场景：DRAFT → AUDITING 审核流转")
    void draftToAuditing流转() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setAuditStatus(TemplateAuditStatusEnum.DRAFT.name());
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus(TemplateAuditStatusEnum.AUDITING.name());
        dto.setAuditRemark("提交审核");

        templateService.audit("1", dto);

        assertEquals(TemplateAuditStatusEnum.AUDITING.name(), entity.getAuditStatus());
        verify(msgTemplateMapper).updateById(entity);
    }

    @Test
    @DisplayName("正常场景：AUDITING → APPROVED 审核通过时同步启用")
    void auditingToApproved同步启用() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setAuditStatus(TemplateAuditStatusEnum.AUDITING.name());
        entity.setStatus("DISABLED");
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus(TemplateAuditStatusEnum.APPROVED.name());

        templateService.audit("1", dto);

        assertEquals(TemplateAuditStatusEnum.APPROVED.name(), entity.getAuditStatus());
        assertEquals("ENABLED", entity.getStatus());
    }

    @Test
    @DisplayName("正常场景：AUDITING → REJECTED 审核驳回时禁用")
    void auditingToRejected禁用() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setAuditStatus(TemplateAuditStatusEnum.AUDITING.name());
        entity.setStatus("ENABLED");
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus(TemplateAuditStatusEnum.REJECTED.name());

        templateService.audit("1", dto);

        assertEquals(TemplateAuditStatusEnum.REJECTED.name(), entity.getAuditStatus());
        assertEquals("DISABLED", entity.getStatus());
    }

    @Test
    @DisplayName("异常场景：APPROVED → AUDITING 非法流转抛 BizException")
    void approvedToAuditing非法流转() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setAuditStatus(TemplateAuditStatusEnum.APPROVED.name());
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus(TemplateAuditStatusEnum.AUDITING.name());

        BizException ex = assertThrows(BizException.class, () -> templateService.audit("1", dto));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：审核状态为空抛 BizException")
    void 审核状态为空抛异常() {
        TemplateAuditDTO dto = new TemplateAuditDTO();

        BizException ex = assertThrows(BizException.class, () -> templateService.audit("1", dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：非法审核状态值抛 BizException")
    void 非法审核状态值抛异常() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("1");
        entity.setAuditStatus(TemplateAuditStatusEnum.DRAFT.name());
        when(msgTemplateMapper.selectById("1")).thenReturn(entity);

        TemplateAuditDTO dto = new TemplateAuditDTO();
        dto.setAuditStatus("INVALID_STATUS");

        BizException ex = assertThrows(BizException.class, () -> templateService.audit("1", dto));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("正常场景：分页查询模板")
    void 分页查询模板() {
        TemplateQueryDTO query = new TemplateQueryDTO();
        query.setPage(1);
        query.setSize(10);
        query.setChannel("SMS");
        Page<MsgTemplateDO> mockPage = new Page<>();
        when(msgTemplateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgTemplateDO> result = templateService.page(query);

        assertNotNull(result);
        verify(msgTemplateMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("边界场景：query 为 null 时使用默认分页")
    void query为null使用默认分页() {
        Page<MsgTemplateDO> mockPage = new Page<>();
        when(msgTemplateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgTemplateDO> result = templateService.page(null);

        assertNotNull(result);
    }
}

package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.FlowTemplateDO;
import com.njydsz.pmis.workflow.mapper.FlowTemplateMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FlowTemplateServiceImpl} P2-9 模板继承与版本化 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>listTemplateVersions — 列出版本（成功/参数空/模板不存在）</li>
 *   <li>getTemplateVersion — version=null 返回最新 / version=N 返回指定 / 版本不存在 / 版本号无效</li>
 *   <li>createNewVersion — 成功创建（旧版本降级）/ 模板不存在 / BPMN XML 为空 / 默认/自定义 versionLabel</li>
 *   <li>cloneTemplate — 成功克隆 / 源模板不存在 / 新编码已存在 / 参数空</li>
 *   <li>inheritFromParent — 成功继承 / 父模板不存在</li>
 *   <li>listInheritedTemplates — 成功列出 / 父模板不存在</li>
 * </ul>
 *
 * <p>注意：Service 依赖 {@link FlowTemplateMapper} 与 {@link FlowDefinitionService}，
 * 但 P2-9 新增方法仅依赖 templateMapper，definitionService 在测试中作为被动 mock 不参与交互。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2-9: 模板继承与版本化 - FlowTemplateServiceImpl")
class FlowTemplateServiceImplTest {

    @Mock
    private FlowTemplateMapper templateMapper;
    @Mock
    private FlowDefinitionService definitionService;

    @InjectMocks
    private FlowTemplateServiceImpl service;

    // ============== 辅助方法 ==============

    private FlowTemplateDO buildTemplate(String code, String name, Integer version,
                                         Integer isLatest, String bpmnXml) {
        FlowTemplateDO t = new FlowTemplateDO();
        t.setId("id_" + code + "_v" + version);
        t.setTemplateCode(code);
        t.setTemplateName(name);
        t.setCategory("HR");
        t.setDescription("desc-" + code);
        t.setIcon("/icon.png");
        t.setBpmnXml(bpmnXml);
        t.setFormPath("/form/" + code);
        t.setUseCount(10);
        t.setSortOrder(100);
        t.setVersion(version);
        t.setVersionLabel("v" + version + ".0");
        t.setInheritType("STANDALONE");
        t.setIsLatest(isLatest);
        return t;
    }

    // ============== listTemplateVersions ==============

    @Nested
    @DisplayName("listTemplateVersions")
    class ListTemplateVersionsTest {

        @Test
        @DisplayName("成功列出多个版本（按 version 降序）")
        void listsAllVersions() {
            String code = "hr_leave";
            FlowTemplateDO v2 = buildTemplate(code, "请假模板", 2, 1, "<xml/>");
            FlowTemplateDO v1 = buildTemplate(code, "请假模板", 1, 0, "<xml-v1/>");
            when(templateMapper.selectVersionsByTemplateCode(code))
                    .thenReturn(List.of(v2, v1));

            List<Map<String, Object>> result = service.listTemplateVersions(code);

            assertEquals(2, result.size());
            assertEquals(2, result.get(0).get("version"));
            assertEquals(1, result.get(1).get("version"));
            // 摘要 Map 不含 BPMN XML
            assertNull(result.get(0).get("bpmnXml"));
            // 包含版本元信息
            assertEquals("v2.0", result.get(0).get("versionLabel"));
            assertEquals(1, result.get(0).get("isLatest"));
            verify(templateMapper).selectVersionsByTemplateCode(code);
        }

        @Test
        @DisplayName("templateCode 为空 → 抛 BizException(BAD_REQUEST)")
        void emptyCodeThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.listTemplateVersions(""));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(templateMapper, never()).selectVersionsByTemplateCode(anyString());
        }

        @Test
        @DisplayName("模板不存在 → 返回空列表（不抛异常）")
        void notFoundReturnsEmpty() {
            String code = "not_exist";
            when(templateMapper.selectVersionsByTemplateCode(code))
                    .thenReturn(List.of());

            List<Map<String, Object>> result = service.listTemplateVersions(code);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ============== getTemplateVersion ==============

    @Nested
    @DisplayName("getTemplateVersion")
    class GetTemplateVersionTest {

        @Test
        @DisplayName("version=null → 返回最新版本")
        void nullVersionReturnsLatest() {
            String code = "hr_leave";
            FlowTemplateDO latest = buildTemplate(code, "请假", 3, 1, "<xml-v3/>");
            when(templateMapper.selectByTemplateCode(code)).thenReturn(latest);

            Map<String, Object> result = service.getTemplateVersion(code, null);

            assertEquals(code, result.get("templateCode"));
            assertEquals(3, result.get("version"));
            assertEquals(1, result.get("isLatest"));
            assertEquals("<xml-v3/>", result.get("bpmnXml"));
            verify(templateMapper).selectByTemplateCode(code);
            verify(templateMapper, never()).selectVersionsByTemplateCode(anyString());
        }

        @Test
        @DisplayName("version=1 → 返回指定版本")
        void specificVersionReturned() {
            String code = "hr_leave";
            FlowTemplateDO v1 = buildTemplate(code, "请假", 1, 0, "<xml-v1/>");
            FlowTemplateDO v2 = buildTemplate(code, "请假", 2, 1, "<xml-v2/>");
            when(templateMapper.selectVersionsByTemplateCode(code))
                    .thenReturn(List.of(v2, v1));

            Map<String, Object> result = service.getTemplateVersion(code, 1);

            assertEquals(1, result.get("version"));
            assertEquals(0, result.get("isLatest"));
            assertEquals("<xml-v1/>", result.get("bpmnXml"));
            verify(templateMapper).selectVersionsByTemplateCode(code);
        }

        @Test
        @DisplayName("version 不存在 → 抛 BizException(NOT_FOUND)")
        void versionNotFoundThrows() {
            String code = "hr_leave";
            FlowTemplateDO v1 = buildTemplate(code, "请假", 1, 1, "<xml/>");
            when(templateMapper.selectVersionsByTemplateCode(code))
                    .thenReturn(List.of(v1));

            BizException ex = assertThrows(BizException.class,
                    () -> service.getTemplateVersion(code, 999));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("version=0 → 抛 BizException(BAD_REQUEST)")
        void invalidVersionThrows() {
            String code = "hr_leave";
            // version<1 校验在 selectVersionsByTemplateCode 调用之前，无需 stub

            BizException ex = assertThrows(BizException.class,
                    () -> service.getTemplateVersion(code, 0));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(templateMapper, never()).selectVersionsByTemplateCode(anyString());
        }

        @Test
        @DisplayName("version=-1 → 抛 BizException(BAD_REQUEST)")
        void negativeVersionThrows() {
            String code = "hr_leave";
            // version<1 校验在 selectVersionsByTemplateCode 调用之前，无需 stub

            BizException ex = assertThrows(BizException.class,
                    () -> service.getTemplateVersion(code, -1));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(templateMapper, never()).selectVersionsByTemplateCode(anyString());
        }

        @Test
        @DisplayName("templateCode 为空 → 抛 BizException(BAD_REQUEST)")
        void emptyCodeThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.getTemplateVersion("", 1));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("version=null 且模板不存在 → 抛 BizException(NOT_FOUND)")
        void nullVersionButTemplateNotFound() {
            String code = "not_exist";
            when(templateMapper.selectByTemplateCode(code)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.getTemplateVersion(code, null));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ============== createNewVersion ==============

    @Nested
    @DisplayName("createNewVersion")
    class CreateNewVersionTest {

        @Test
        @DisplayName("成功创建新版本（旧版本降级 + 默认 versionLabel）")
        void createsNewVersionWithDefaultLabel() {
            String code = "hr_leave";
            FlowTemplateDO current = buildTemplate(code, "请假", 2, 1, "<xml-v2/>");
            when(templateMapper.selectByTemplateCode(code)).thenReturn(current);
            when(templateMapper.selectMaxVersion(code)).thenReturn(2);

            Integer newVersion = service.createNewVersion(code, null);

            assertEquals(3, newVersion);
            // 旧版本降级
            verify(templateMapper).markAsNotLatest(code);
            verify(templateMapper).selectMaxVersion(code);
            // 插入新版本
            org.mockito.ArgumentCaptor<FlowTemplateDO> captor =
                    org.mockito.ArgumentCaptor.forClass(FlowTemplateDO.class);
            verify(templateMapper).insert(captor.capture());
            FlowTemplateDO inserted = captor.getValue();
            assertEquals(3, inserted.getVersion());
            assertEquals(1, inserted.getIsLatest());
            assertEquals("v3.0", inserted.getVersionLabel());
            assertEquals("<xml-v2/>", inserted.getBpmnXml());
            // 沿用旧版本的 inheritType
            assertEquals("STANDALONE", inserted.getInheritType());
        }

        @Test
        @DisplayName("成功创建新版本（自定义 versionLabel）")
        void createsNewVersionWithCustomLabel() {
            String code = "hr_leave";
            FlowTemplateDO current = buildTemplate(code, "请假", 1, 1, "<xml/>");
            when(templateMapper.selectByTemplateCode(code)).thenReturn(current);
            when(templateMapper.selectMaxVersion(code)).thenReturn(1);

            Integer newVersion = service.createNewVersion(code, "v2.0-rc1");

            assertEquals(2, newVersion);
            org.mockito.ArgumentCaptor<FlowTemplateDO> captor =
                    org.mockito.ArgumentCaptor.forClass(FlowTemplateDO.class);
            verify(templateMapper).insert(captor.capture());
            assertEquals("v2.0-rc1", captor.getValue().getVersionLabel());
            assertEquals(2, captor.getValue().getVersion());
        }

        @Test
        @DisplayName("templateCode 为空 → 抛 BizException(BAD_REQUEST)")
        void emptyCodeThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.createNewVersion("", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(templateMapper, never()).markAsNotLatest(anyString());
            verify(templateMapper, never()).insert(any(FlowTemplateDO.class));
        }

        @Test
        @DisplayName("模板不存在 → 抛 BizException(NOT_FOUND)")
        void templateNotFoundThrows() {
            String code = "not_exist";
            when(templateMapper.selectByTemplateCode(code)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.createNewVersion(code, null));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            verify(templateMapper, never()).markAsNotLatest(anyString());
        }

        @Test
        @DisplayName("BPMN XML 为空 → 抛 BizException(BAD_REQUEST)")
        void emptyBpmnXmlThrows() {
            String code = "hr_leave";
            FlowTemplateDO current = buildTemplate(code, "请假", 1, 1, "");
            when(templateMapper.selectByTemplateCode(code)).thenReturn(current);

            BizException ex = assertThrows(BizException.class,
                    () -> service.createNewVersion(code, null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(templateMapper, never()).markAsNotLatest(anyString());
        }

        @Test
        @DisplayName("maxVersion 返回 null → 新版本号 = 1")
        void maxVersionNullReturns1() {
            // 边界场景：理论上 selectByTemplateCode 返回非 null 时 maxVersion 不应为 null，
            // 但代码做了防御性处理，测试覆盖此分支
            String code = "hr_leave";
            FlowTemplateDO current = buildTemplate(code, "请假", 1, 1, "<xml/>");
            when(templateMapper.selectByTemplateCode(code)).thenReturn(current);
            when(templateMapper.selectMaxVersion(code)).thenReturn(null);

            Integer newVersion = service.createNewVersion(code, null);

            assertEquals(1, newVersion);
            verify(templateMapper).markAsNotLatest(code);
        }
    }

    // ============== cloneTemplate ==============

    @Nested
    @DisplayName("cloneTemplate")
    class CloneTemplateTest {

        @Test
        @DisplayName("成功克隆（CLONE 类型，自定义 category）")
        void clonesSuccessfullyWithCustomCategory() {
            String sourceCode = "hr_leave";
            String newCode = "hr_leave_v2";
            FlowTemplateDO source = buildTemplate(sourceCode, "请假", 1, 1, "<xml/>");
            when(templateMapper.selectByTemplateCode(sourceCode)).thenReturn(source);
            when(templateMapper.selectByTemplateCode(newCode)).thenReturn(null);

            String result = service.cloneTemplate(sourceCode, newCode, "请假v2", "FINANCE");

            assertEquals(newCode, result);
            org.mockito.ArgumentCaptor<FlowTemplateDO> captor =
                    org.mockito.ArgumentCaptor.forClass(FlowTemplateDO.class);
            verify(templateMapper).insert(captor.capture());
            FlowTemplateDO inserted = captor.getValue();
            assertEquals(newCode, inserted.getTemplateCode());
            assertEquals("请假v2", inserted.getTemplateName());
            assertEquals("FINANCE", inserted.getCategory());
            assertEquals(1, inserted.getVersion());
            assertEquals(1, inserted.getIsLatest());
            assertEquals("CLONE", inserted.getInheritType());
            assertEquals(source.getId(), inserted.getParentTemplateId());
            assertEquals(0, inserted.getUseCount());
        }

        @Test
        @DisplayName("成功克隆（默认沿用源模板 category）")
        void clonesWithDefaultCategory() {
            String sourceCode = "hr_leave";
            String newCode = "hr_leave_copy";
            FlowTemplateDO source = buildTemplate(sourceCode, "请假", 2, 1, "<xml/>");
            when(templateMapper.selectByTemplateCode(sourceCode)).thenReturn(source);
            when(templateMapper.selectByTemplateCode(newCode)).thenReturn(null);

            service.cloneTemplate(sourceCode, newCode, "请假副本", null);

            org.mockito.ArgumentCaptor<FlowTemplateDO> captor =
                    org.mockito.ArgumentCaptor.forClass(FlowTemplateDO.class);
            verify(templateMapper).insert(captor.capture());
            // 默认沿用源模板的 category=HR
            assertEquals("HR", captor.getValue().getCategory());
        }

        @Test
        @DisplayName("源模板不存在 → 抛 BizException(NOT_FOUND)")
        void sourceNotFoundThrows() {
            String sourceCode = "not_exist";
            when(templateMapper.selectByTemplateCode(sourceCode)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.cloneTemplate(sourceCode, "new_code", "新模板", null));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            verify(templateMapper, never()).insert(any(FlowTemplateDO.class));
        }

        @Test
        @DisplayName("新编码已存在 → 抛 BizException(BAD_REQUEST)")
        void newCodeExistsThrows() {
            String sourceCode = "hr_leave";
            String newCode = "hr_leave_existing";
            when(templateMapper.selectByTemplateCode(sourceCode))
                    .thenReturn(buildTemplate(sourceCode, "请假", 1, 1, "<xml/>"));
            when(templateMapper.selectByTemplateCode(newCode))
                    .thenReturn(buildTemplate(newCode, "已存在", 1, 1, "<xml/>"));

            BizException ex = assertThrows(BizException.class,
                    () -> service.cloneTemplate(sourceCode, newCode, "新模板", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(templateMapper, never()).insert(any(FlowTemplateDO.class));
        }

        @Test
        @DisplayName("源 templateCode 为空 → 抛 BizException(BAD_REQUEST)")
        void emptySourceCodeThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.cloneTemplate("", "new_code", "新模板", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("新编码为空 → 抛 BizException(BAD_REQUEST)")
        void emptyNewCodeThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.cloneTemplate("hr_leave", "", "新模板", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("新名称为空 → 抛 BizException(BAD_REQUEST)")
        void emptyNewNameThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.cloneTemplate("hr_leave", "new_code", "", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }
    }

    // ============== inheritFromParent ==============

    @Nested
    @DisplayName("inheritFromParent")
    class InheritFromParentTest {

        @Test
        @DisplayName("成功继承（INHERIT 类型，保留 parent_template_id 关联）")
        void inheritsSuccessfully() {
            String parentCode = "hr_leave";
            String childCode = "hr_leave_child";
            FlowTemplateDO parent = buildTemplate(parentCode, "请假", 1, 1, "<xml/>");
            when(templateMapper.selectByTemplateCode(parentCode)).thenReturn(parent);
            when(templateMapper.selectByTemplateCode(childCode)).thenReturn(null);

            String result = service.inheritFromParent(parentCode, childCode, "请假子模板", null);

            assertEquals(childCode, result);
            org.mockito.ArgumentCaptor<FlowTemplateDO> captor =
                    org.mockito.ArgumentCaptor.forClass(FlowTemplateDO.class);
            verify(templateMapper).insert(captor.capture());
            FlowTemplateDO inserted = captor.getValue();
            assertEquals("INHERIT", inserted.getInheritType());
            assertEquals(parent.getId(), inserted.getParentTemplateId());
            assertEquals(1, inserted.getVersion());
            assertEquals(1, inserted.getIsLatest());
        }

        @Test
        @DisplayName("父模板不存在 → 抛 BizException(NOT_FOUND)")
        void parentNotFoundThrows() {
            String parentCode = "not_exist";
            when(templateMapper.selectByTemplateCode(parentCode)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.inheritFromParent(parentCode, "new_code", "新模板", null));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            verify(templateMapper, never()).insert(any(FlowTemplateDO.class));
        }
    }

    // ============== listInheritedTemplates ==============

    @Nested
    @DisplayName("listInheritedTemplates")
    class ListInheritedTemplatesTest {

        @Test
        @DisplayName("成功列出继承子模板（仅最新版本）")
        void listsInheritedChildren() {
            String parentCode = "hr_leave";
            FlowTemplateDO parent = buildTemplate(parentCode, "请假", 1, 1, "<xml/>");
            FlowTemplateDO child1 = buildTemplate("hr_leave_c1", "子1", 1, 1, "<xml/>");
            child1.setInheritType("INHERIT");
            FlowTemplateDO child2 = buildTemplate("hr_leave_c2", "子2", 1, 1, "<xml/>");
            child2.setInheritType("CLONE");
            when(templateMapper.selectByTemplateCode(parentCode)).thenReturn(parent);
            when(templateMapper.selectByParentTemplateId(parent.getId()))
                    .thenReturn(List.of(child1, child2));

            List<Map<String, Object>> result = service.listInheritedTemplates(parentCode);

            assertEquals(2, result.size());
            assertEquals("INHERIT", result.get(0).get("inheritType"));
            assertEquals("CLONE", result.get(1).get("inheritType"));
            verify(templateMapper).selectByParentTemplateId(parent.getId());
        }

        @Test
        @DisplayName("父模板不存在 → 抛 BizException(NOT_FOUND)")
        void parentNotFoundThrows() {
            String parentCode = "not_exist";
            when(templateMapper.selectByTemplateCode(parentCode)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.listInheritedTemplates(parentCode));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            verify(templateMapper, never()).selectByParentTemplateId(anyString());
        }

        @Test
        @DisplayName("parentCode 为空 → 抛 BizException(BAD_REQUEST)")
        void emptyCodeThrows() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.listInheritedTemplates(""));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("无继承子模板 → 返回空列表")
        void noChildrenReturnsEmpty() {
            String parentCode = "hr_leave";
            FlowTemplateDO parent = buildTemplate(parentCode, "请假", 1, 1, "<xml/>");
            when(templateMapper.selectByTemplateCode(parentCode)).thenReturn(parent);
            when(templateMapper.selectByParentTemplateId(parent.getId()))
                    .thenReturn(List.of());

            List<Map<String, Object>> result = service.listInheritedTemplates(parentCode);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}

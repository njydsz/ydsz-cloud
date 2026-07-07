package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowAttachmentPreviewVO;
import com.njydsz.pmis.workflow.entity.FlowAttachmentDO;
import com.njydsz.pmis.workflow.mapper.FlowAttachmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * P2-3: 附件在线预览单元测试。
 *
 * <p>聚焦测试 {@link FlowAttachmentServiceImpl#previewAttachment} 的文件类型分类、
 * 预览 URL 构建逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@DisplayName("P2-3 附件在线预览测试")
@ExtendWith(MockitoExtension.class)
class FlowAttachmentServiceImplTest {

    @Mock
    private FlowAttachmentMapper attachmentMapper;

    @InjectMocks
    private FlowAttachmentServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        // @Value 字段默认不注入，需手动反射设置（默认空串 = 未配置预览服务）
        setPreviewServerUrl("");
    }

    private void setPreviewServerUrl(String url) throws Exception {
        Field field = FlowAttachmentServiceImpl.class.getDeclaredField("previewServerUrl");
        field.setAccessible(true);
        field.set(service, url);
    }

    private FlowAttachmentDO buildAttachment(String id, String fileExt, String downloadUrl) {
        FlowAttachmentDO attachment = new FlowAttachmentDO();
        attachment.setId(id);
        attachment.setFileName("test." + fileExt);
        attachment.setFileExt(fileExt);
        attachment.setDownloadUrl(downloadUrl);
        attachment.setContentType("application/octet-stream");
        attachment.setDeleted(0);
        return attachment;
    }

    // ============================== 文件类型分类测试 ==============================

    @Nested
    @DisplayName("文件类型分类 classifyPreviewType")
    class ClassifyPreviewTypeTest {

        @Test
        @DisplayName("jpg → IMAGE")
        void jpg_returnsImage() {
            assertEquals("IMAGE", service.classifyPreviewType("jpg"));
        }

        @Test
        @DisplayName("png → IMAGE")
        void png_returnsImage() {
            assertEquals("IMAGE", service.classifyPreviewType("png"));
        }

        @Test
        @DisplayName("pdf → PDF")
        void pdf_returnsPdf() {
            assertEquals("PDF", service.classifyPreviewType("pdf"));
        }

        @Test
        @DisplayName("mp4 → VIDEO")
        void mp4_returnsVideo() {
            assertEquals("VIDEO", service.classifyPreviewType("mp4"));
        }

        @Test
        @DisplayName("txt → TEXT")
        void txt_returnsText() {
            assertEquals("TEXT", service.classifyPreviewType("txt"));
        }

        @Test
        @DisplayName("docx → OFFICE")
        void docx_returnsOffice() {
            assertEquals("OFFICE", service.classifyPreviewType("docx"));
        }

        @Test
        @DisplayName("xlsx → OFFICE")
        void xlsx_returnsOffice() {
            assertEquals("OFFICE", service.classifyPreviewType("xlsx"));
        }

        @Test
        @DisplayName("空扩展名 → UNSUPPORTED")
        void emptyExt_returnsUnsupported() {
            assertEquals("UNSUPPORTED", service.classifyPreviewType(""));
        }

        @Test
        @DisplayName("null 扩展名 → UNSUPPORTED")
        void nullExt_returnsUnsupported() {
            assertEquals("UNSUPPORTED", service.classifyPreviewType(null));
        }

        @Test
        @DisplayName("未知扩展名（如 exe）→ UNSUPPORTED")
        void unknownExt_returnsUnsupported() {
            assertEquals("UNSUPPORTED", service.classifyPreviewType("exe"));
        }
    }

    // ============================== 预览接口测试 ==============================

    @Nested
    @DisplayName("预览接口 previewAttachment")
    class PreviewAttachmentTest {

        @Test
        @DisplayName("附件不存在 → 抛 NOT_FOUND")
        void attachmentNotFound_throwsNotFound() {
            when(attachmentMapper.selectById("att-1")).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.previewAttachment("att-1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_c5d6e7f8", ex.getErrorMessage());
        }

        @Test
        @DisplayName("附件已删除 → 抛 NOT_FOUND")
        void deletedAttachment_throwsNotFound() {
            FlowAttachmentDO attachment = buildAttachment("att-1", "jpg", "http://example.com/a.jpg");
            attachment.setDeleted(1);
            when(attachmentMapper.selectById("att-1")).thenReturn(attachment);

            assertThrows(BizException.class, () -> service.previewAttachment("att-1"));
        }

        @Test
        @DisplayName("IMAGE 类型 → previewUrl=downloadUrl，previewable=true")
        void imageType_returnsDownloadUrl() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "jpg", "http://example.com/a.jpg"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("IMAGE", vo.getPreviewType());
            assertEquals("http://example.com/a.jpg", vo.getPreviewUrl());
            assertEquals("http://example.com/a.jpg", vo.getDownloadUrl());
            assertTrue(vo.isPreviewable());
        }

        @Test
        @DisplayName("PDF 类型 → previewable=true")
        void pdfType_previewable() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "pdf", "http://example.com/a.pdf"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("PDF", vo.getPreviewType());
            assertTrue(vo.isPreviewable());
        }

        @Test
        @DisplayName("VIDEO 类型 → previewable=true")
        void videoType_previewable() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "mp4", "http://example.com/a.mp4"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("VIDEO", vo.getPreviewType());
            assertTrue(vo.isPreviewable());
        }

        @Test
        @DisplayName("TEXT 类型 → previewable=true")
        void textType_previewable() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "txt", "http://example.com/a.txt"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("TEXT", vo.getPreviewType());
            assertTrue(vo.isPreviewable());
        }

        @Test
        @DisplayName("OFFICE 类型 + 未配置预览服务 → previewable=false")
        void officeType_withoutPreviewServer_notPreviewable() throws Exception {
            setPreviewServerUrl("");
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "docx", "http://example.com/a.docx"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("OFFICE", vo.getPreviewType());
            assertNull(vo.getPreviewUrl(), "未配置预览服务时 previewUrl 应为 null");
            assertFalse(vo.isPreviewable());
            assertNotNull(vo.getDownloadUrl(), "downloadUrl 始终提供");
        }

        @Test
        @DisplayName("OFFICE 类型 + 配置预览服务（含 {url} 占位符）→ previewUrl 替换后 URL")
        void officeType_withPlaceholderUrl_replacesUrl() throws Exception {
            setPreviewServerUrl("http://preview.example.com/onlinePreview?url={url}");
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "docx", "http://example.com/a.docx"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("OFFICE", vo.getPreviewType());
            assertTrue(vo.isPreviewable());
            // previewUrl 应包含 URLEncoder.encode("http://example.com/a.docx")
            String encoded = java.net.URLEncoder.encode("http://example.com/a.docx",
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("http://preview.example.com/onlinePreview?url=" + encoded,
                    vo.getPreviewUrl());
        }

        @Test
        @DisplayName("OFFICE 类型 + 配置预览服务（不含占位符）→ previewUrl 拼接 downloadUrl")
        void officeType_withoutPlaceholder_concatenatesUrl() throws Exception {
            setPreviewServerUrl("http://preview.example.com/view?");
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "xlsx", "http://example.com/a.xlsx"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("OFFICE", vo.getPreviewType());
            assertTrue(vo.isPreviewable());
            assertEquals("http://preview.example.com/view?http://example.com/a.xlsx",
                    vo.getPreviewUrl());
        }

        @Test
        @DisplayName("UNSUPPORTED 类型 → previewable=false")
        void unsupportedType_notPreviewable() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "exe", "http://example.com/a.exe"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("UNSUPPORTED", vo.getPreviewType());
            assertFalse(vo.isPreviewable());
        }

        @Test
        @DisplayName("downloadUrl 为空 → previewUrl=null，previewable=false")
        void emptyDownloadUrl_returnsNullPreviewUrl() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "jpg", null));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("IMAGE", vo.getPreviewType());
            assertNull(vo.getPreviewUrl());
            assertFalse(vo.isPreviewable());
        }

        @Test
        @DisplayName("扩展名大写 → 归一化为小写后分类")
        void upperCaseExt_normalizedToLower() {
            when(attachmentMapper.selectById("att-1"))
                    .thenReturn(buildAttachment("att-1", "JPG", "http://example.com/a.jpg"));

            FlowAttachmentPreviewVO vo = service.previewAttachment("att-1");

            assertEquals("IMAGE", vo.getPreviewType());
            assertEquals("jpg", vo.getFileExt());
            assertTrue(vo.isPreviewable());
        }
    }
}

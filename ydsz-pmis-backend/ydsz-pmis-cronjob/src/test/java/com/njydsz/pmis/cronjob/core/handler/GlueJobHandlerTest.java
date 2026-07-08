package com.njydsz.pmis.cronjob.core.handler;

import com.njydsz.pmis.common.job.JobContextHolder;
import com.njydsz.pmis.cronjob.entity.GlueCodeDO;
import com.njydsz.pmis.cronjob.service.GlueCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GlueJobHandler} 单元测试（P1-2 GLUE 在线编码）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>编译执行 Groovy 脚本（实现 JobHandler 接口）</li>
 *   <li>编译执行 Groovy 脚本（脚本式 execute(String) 方法）</li>
 *   <li>编译失败抛 RuntimeException</li>
 *   <li>执行失败抛 RuntimeException</li>
 *   <li>代码缓存：相同代码不重新编译</li>
 *   <li>代码变更后重新编译</li>
 *   <li>jobId 为空抛 IllegalStateException</li>
 *   <li>GLUE 代码不存在抛 IllegalStateException</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("GlueJobHandler GLUE 任务处理器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlueJobHandlerTest {

    @Mock
    private ObjectProvider<GlueCodeService> glueCodeServiceProvider;

    @Mock
    private GlueCodeService glueCodeService;

    private GlueJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlueJobHandler(glueCodeServiceProvider);
        when(glueCodeServiceProvider.getIfAvailable()).thenReturn(glueCodeService);
    }

    @AfterEach
    void tearDown() {
        JobContextHolder.clear();
        handler.clearCacheForTest();
    }

    @Test
    @DisplayName("execute: 编译执行实现 JobHandler 接口的 Groovy 代码")
    void execute_jobHandlerImpl_returnsResult() throws Exception {
        String source = ""
                + "package test;\n"
                + "import com.njydsz.pmis.common.job.JobHandler;\n"
                + "public class MyHandler implements JobHandler {\n"
                + "    public Object execute(String params) {\n"
                + "        return \"echo:\" + params;\n"
                + "    }\n"
                + "}\n";
        setupGlueCode("job-1", source);
        JobContextHolder.set("job-1", "test-key-1");

        Object result = handler.execute("hello");

        assertEquals("echo:hello", result);
        verify(glueCodeService, times(1)).getLatest("job-1");
    }

    @Test
    @DisplayName("execute: 编译执行脚本式 execute(String) 方法")
    void execute_scriptStyle_returnsResult() throws Exception {
        String source = ""
                + "def execute(String params) {\n"
                + "    return 'script:' + params\n"
                + "}\n";
        setupGlueCode("job-2", source);
        JobContextHolder.set("job-2", "test-key-2");

        Object result = handler.execute("world");

        assertEquals("script:world", result);
    }

    @Test
    @DisplayName("execute: 编译失败抛 RuntimeException")
    void execute_compileFails_throwsRuntimeException() {
        // 缺少括号的非法 Groovy 代码
        String invalidSource = "def execute(String params) { return 'broken' ";
        setupGlueCode("job-3", invalidSource);
        JobContextHolder.set("job-3", "test-key-3");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.execute("p"));
        assertTrue(ex.getMessage().contains("编译失败"), "异常信息应包含编译失败");
    }

    @Test
    @DisplayName("execute: 脚本执行抛异常时抛 RuntimeException")
    void execute_scriptThrows_throwsRuntimeException() {
        String source = ""
                + "def execute(String params) {\n"
                + "    throw new RuntimeException('script boom')\n"
                + "}\n";
        setupGlueCode("job-4", source);
        JobContextHolder.set("job-4", "test-key-4");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.execute("p"));
        // 异常链中应包含脚本抛出的异常信息
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause.getMessage().contains("script boom")
                || ex.getMessage().contains("script boom"),
                "异常信息应包含 script boom");
    }

    @Test
    @DisplayName("execute: 相同代码命中缓存不重新编译")
    void execute_sameCode_hitsCache() throws Exception {
        String source = ""
                + "def execute(String params) {\n"
                + "    return 'cached'\n"
                + "}\n";
        setupGlueCode("job-5", source);
        JobContextHolder.set("job-5", "test-key-5");

        // 首次执行：编译并缓存
        handler.execute("p1");
        assertEquals(1, handler.cacheSizeForTest(), "首次编译后缓存应包含 1 条");

        // 第二次执行：命中缓存，不重新编译
        Object result = handler.execute("p2");
        assertEquals("cached", result);
        assertEquals(1, handler.cacheSizeForTest(), "相同代码缓存条目数应保持 1");
    }

    @Test
    @DisplayName("execute: 代码变更后重新编译")
    void execute_codeChanged_recompiles() throws Exception {
        // 第一版代码
        String source1 = ""
                + "def execute(String params) {\n"
                + "    return 'v1'\n"
                + "}\n";
        setupGlueCode("job-6", source1);
        JobContextHolder.set("job-6", "test-key-6");

        Object r1 = handler.execute("p");
        assertEquals("v1", r1);
        assertEquals(1, handler.cacheSizeForTest());

        // 第二版代码（不同 hashCode）
        String source2 = ""
                + "def execute(String params) {\n"
                + "    return 'v2'\n"
                + "}\n";
        setupGlueCode("job-6", source2);

        Object r2 = handler.execute("p");
        assertEquals("v2", r2);
        // 新代码会添加新的缓存条目
        assertEquals(2, handler.cacheSizeForTest());
    }

    @Test
    @DisplayName("execute: jobId 为空抛 IllegalStateException")
    void execute_emptyJobId_throwsException() {
        JobContextHolder.clear();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> handler.execute("p"));
        assertTrue(ex.getMessage().contains("jobId"));
        verify(glueCodeService, never()).getLatest(anyString());
    }

    @Test
    @DisplayName("execute: GLUE 代码不存在抛 IllegalStateException")
    void execute_glueCodeNotFound_throwsException() {
        JobContextHolder.set("job-empty", "key");
        when(glueCodeService.getLatest("job-empty")).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> handler.execute("p"));
        assertTrue(ex.getMessage().contains("未找到 GLUE 代码"));
    }

    @Test
    @DisplayName("execute: GlueCodeService 未注册抛 IllegalStateException")
    void execute_glueServiceUnavailable_throwsException() {
        when(glueCodeServiceProvider.getIfAvailable()).thenReturn(null);
        JobContextHolder.set("job-7", "test-key-7");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> handler.execute("p"));
        assertTrue(ex.getMessage().contains("GlueCodeService 未注册"));
    }

    @Test
    @DisplayName("execute: GLUE 代码为空字符串抛 IllegalStateException")
    void execute_blankSource_throwsException() {
        JobContextHolder.set("job-8", "test-key-8");
        GlueCodeDO empty = new GlueCodeDO();
        empty.setJobId("job-8");
        empty.setSourceCode("   ");
        empty.setVersion(1);
        when(glueCodeService.getLatest("job-8")).thenReturn(empty);

        assertThrows(IllegalStateException.class, () -> handler.execute("p"));
    }

    /**
     * 设置 GlueCodeService.getLatest 返回指定源代码。
     */
    private void setupGlueCode(String jobId, String source) {
        GlueCodeDO glueCode = new GlueCodeDO();
        glueCode.setJobId(jobId);
        glueCode.setSourceCode(source);
        glueCode.setLanguage("GROOVY");
        glueCode.setVersion(1);
        when(glueCodeService.getLatest(jobId)).thenReturn(glueCode);
    }
}

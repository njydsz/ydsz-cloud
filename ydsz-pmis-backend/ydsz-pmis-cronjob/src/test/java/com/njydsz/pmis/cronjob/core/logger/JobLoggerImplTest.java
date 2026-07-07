package com.njydsz.pmis.cronjob.core.logger;

import com.njydsz.pmis.cronjob.entity.JobLogContentDO;
import com.njydsz.pmis.cronjob.service.JobLogContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link JobLoggerImpl} 单元测试（P0-2 在线日志白屏化）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>info/warn/error/debug 基本写入</li>
 *   <li>占位符替换（SLF4J 风格 {@code {}}）</li>
 *   <li>缓冲区满 100 行自动 flush</li>
 *   <li>error(message, throwable) 异常堆栈写入</li>
 *   <li>内容超 4000 字符截断</li>
 *   <li>flush() 手动刷新</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobLoggerImpl 在线日志器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobLoggerImplTest {

    @Mock
    private JobLogContentService jobLogContentService;

    private JobLoggerImpl jobLogger;

    @BeforeEach
    void setUp() {
        jobLogger = new JobLoggerImpl("log-001", "demo-job", jobLogContentService);
    }

    @Test
    @DisplayName("info: 写入单行日志后 flush 应落库")
    void info_singleLine_flushedToDb() {
        jobLogger.info("开始处理");
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals(1, captor.getValue().size());
        JobLogContentDO line = captor.getValue().get(0);
        assertEquals("log-001", line.getLogId());
        assertEquals("demo-job", line.getJobKey());
        assertEquals(1, line.getLineNo());
        assertEquals("INFO", line.getLogLevel());
        assertEquals("开始处理", line.getContent());
        assertEquals(0, line.getDeleted());
        assertNotNull(line.getCreatedAt());
    }

    @Test
    @DisplayName("warn: 写入 WARN 级别日志")
    void warn_writesWarnLevel() {
        jobLogger.warn("警告信息");
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("WARN", captor.getValue().get(0).getLogLevel());
        assertEquals("警告信息", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("error: 写入 ERROR 级别日志")
    void error_writesErrorLevel() {
        jobLogger.error("发生错误");
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("ERROR", captor.getValue().get(0).getLogLevel());
        assertEquals("发生错误", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("debug: 写入 DEBUG 级别日志")
    void debug_writesDebugLevel() {
        jobLogger.debug("调试信息");
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("DEBUG", captor.getValue().get(0).getLogLevel());
        assertEquals("调试信息", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("info(format, args): 占位符替换正确")
    void info_withPlaceholders_replacedCorrectly() {
        jobLogger.info("处理 {} 条, 耗时 {}ms", 10, 200);
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("处理 10 条, 耗时 200ms", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("info(format, args): 单个占位符替换")
    void info_singlePlaceholder_replaced() {
        jobLogger.info("处理 {} 条", 10);
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("处理 10 条", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("info(format, args): 参数不足时保留剩余占位符")
    void info_insufficientArgs_keepsPlaceholder() {
        jobLogger.info("a={} b={}", 1);
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("a=1 b={}", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("缓冲区满 100 行自动 flush")
    void bufferFull_autoFlush() {
        for (int i = 0; i < 100; i++) {
            jobLogger.info("行 " + i);
        }
        // 满 100 行应自动触发一次 batchSave
        verify(jobLogContentService, times(1)).batchSave(any());

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals(100, captor.getValue().size());
        // 行号从 1 递增到 100
        assertEquals(1, captor.getValue().get(0).getLineNo());
        assertEquals(100, captor.getValue().get(99).getLineNo());
    }

    @Test
    @DisplayName("缓冲区满 100 行后再写 50 行, flush 时应只写入剩余 50 行")
    void bufferFullThenPartialFlush() {
        for (int i = 0; i < 150; i++) {
            jobLogger.info("行 " + i);
        }
        // 第一次 100 行自动 flush, 剩余 50 行在手动 flush 时写入
        verify(jobLogContentService, times(1)).batchSave(any());
        jobLogger.flush();
        verify(jobLogContentService, times(2)).batchSave(any());
    }

    @Test
    @DisplayName("error(message, throwable): 异常堆栈追加到消息后")
    void error_withThrowable_stackTraceAppended() {
        Throwable t = new RuntimeException("空指针");
        jobLogger.error("执行失败", t);
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        String content = captor.getValue().get(0).getContent();
        assertTrue(content.startsWith("执行失败\n"), "消息应以原消息开头");
        assertTrue(content.contains("RuntimeException"), "应包含异常类型");
        assertTrue(content.contains("空指针"), "应包含异常消息");
    }

    @Test
    @DisplayName("error(message, null): throwable 为 null 时不追加堆栈")
    void error_nullThrowable_noStackTrace() {
        jobLogger.error("失败", (Throwable) null);
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals("失败", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("内容超 4000 字符截断并追加标记")
    void contentExceeds4000_truncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("x");
        }
        jobLogger.info(sb.toString());
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        String content = captor.getValue().get(0).getContent();
        assertEquals(4000 + "...[truncated]".length(), content.length());
        assertTrue(content.endsWith("...[truncated]"));
    }

    @Test
    @DisplayName("内容等于 4000 字符不截断")
    void contentEquals4000_notTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            sb.append("x");
        }
        jobLogger.info(sb.toString());
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        String content = captor.getValue().get(0).getContent();
        assertEquals(4000, content.length());
    }

    @Test
    @DisplayName("flush: 空缓冲区不调用 batchSave")
    void flush_emptyBuffer_noBatchSave() {
        jobLogger.flush();
        verify(jobLogContentService, never()).batchSave(any());
    }

    @Test
    @DisplayName("flush 后缓冲区清空, 再次 flush 不调用 batchSave")
    void flush_clearsBuffer() {
        jobLogger.info("第一行");
        jobLogger.flush();
        verify(jobLogContentService, times(1)).batchSave(any());

        // 再次 flush, 缓冲区已空, 不应再调用
        jobLogger.flush();
        verify(jobLogContentService, times(1)).batchSave(any());
    }

    @Test
    @DisplayName("batchSave 抛异常时不影响后续日志写入")
    void batchSaveThrows_doesNotAffectSubsequent() {
        // 第一次 flush 抛异常
        org.mockito.Mockito.doThrow(new RuntimeException("DB 异常"))
                .when(jobLogContentService).batchSave(any());

        jobLogger.info("第一行");
        jobLogger.flush(); // 异常被吞掉

        // 重置 mock, 后续应正常写入
        org.mockito.Mockito.reset(jobLogContentService);
        jobLogger.info("第二行");
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        assertEquals(1, captor.getValue().size());
        assertEquals("第二行", captor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("Service 为 null 时日志被丢弃, 不抛异常")
    void serviceNull_logsDropped() {
        JobLoggerImpl nullServiceLogger = new JobLoggerImpl("log-002", "job-2", null);
        nullServiceLogger.info("测试");
        nullServiceLogger.flush(); // 不应抛异常
    }

    @Test
    @DisplayName("行号从 1 开始递增")
    void lineNo_incrementsFromOne() {
        jobLogger.info("行1");
        jobLogger.info("行2");
        jobLogger.info("行3");
        jobLogger.flush();

        ArgumentCaptor<List<JobLogContentDO>> captor = captureBatchSave();
        List<JobLogContentDO> lines = captor.getValue();
        assertEquals(3, lines.size());
        assertEquals(1, lines.get(0).getLineNo());
        assertEquals(2, lines.get(1).getLineNo());
        assertEquals(3, lines.get(2).getLineNo());
    }

    /**
     * 捕获 batchSave 调用的参数列表。
     */
    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<JobLogContentDO>> captureBatchSave() {
        ArgumentCaptor<List<JobLogContentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobLogContentService).batchSave(captor.capture());
        return captor;
    }
}

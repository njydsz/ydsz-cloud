package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.cronjob.entity.JobLogContentDO;
import com.njydsz.pmis.cronjob.mapper.JobLogContentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobLogContentServiceImpl} 单元测试（P0-2 在线日志白屏化）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>batchSave: 空列表 / null / 正常列表</li>
 *   <li>pageByLogId: 分页 offset 计算</li>
 *   <li>listAfterLine: 透传 mapper</li>
 *   <li>countByLogId: 透传 mapper</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobLogContentServiceImpl 任务日志内容服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobLogContentServiceImplTest {

    @Mock
    private JobLogContentMapper jobLogContentMapper;

    @InjectMocks
    private JobLogContentServiceImpl jobLogContentService;

    // ==================== batchSave ====================

    @Test
    @DisplayName("batchSave: 空列表不调用 insert")
    void batchSave_emptyList_noInsert() {
        jobLogContentService.batchSave(Collections.emptyList());
        verify(jobLogContentMapper, never()).insert(any(JobLogContentDO.class));
    }

    @Test
    @DisplayName("batchSave: null 列表不调用 insert")
    void batchSave_nullList_noInsert() {
        jobLogContentService.batchSave(null);
        verify(jobLogContentMapper, never()).insert(any(JobLogContentDO.class));
    }

    @Test
    @DisplayName("batchSave: 正常列表逐条 insert")
    void batchSave_normalList_insertEach() {
        List<JobLogContentDO> contents = new ArrayList<>();
        contents.add(buildLine("line-1", 1));
        contents.add(buildLine("line-2", 2));
        contents.add(buildLine("line-3", 3));

        jobLogContentService.batchSave(contents);

        verify(jobLogContentMapper, times(3)).insert(any(JobLogContentDO.class));
        verify(jobLogContentMapper).insert(contents.get(0));
        verify(jobLogContentMapper).insert(contents.get(1));
        verify(jobLogContentMapper).insert(contents.get(2));
    }

    @Test
    @DisplayName("batchSave: 单条列表正确 insert")
    void batchSave_singleItem_insertOne() {
        JobLogContentDO line = buildLine("line-1", 1);
        jobLogContentService.batchSave(List.of(line));

        verify(jobLogContentMapper, times(1)).insert(line);
    }

    // ==================== pageByLogId ====================

    @Test
    @DisplayName("pageByLogId: 第一页 offset=0")
    void pageByLogId_firstPage_offsetZero() {
        List<JobLogContentDO> expected = List.of(buildLine("line-1", 1));
        when(jobLogContentMapper.selectByLogId("log-001", 0, 100)).thenReturn(expected);

        List<JobLogContentDO> result = jobLogContentService.pageByLogId("log-001", 1, 100);

        assertEquals(expected, result);
        verify(jobLogContentMapper).selectByLogId("log-001", 0, 100);
    }

    @Test
    @DisplayName("pageByLogId: 第二页 offset=size")
    void pageByLogId_secondPage_offsetSize() {
        List<JobLogContentDO> expected = List.of(buildLine("line-101", 101));
        when(jobLogContentMapper.selectByLogId("log-001", 100, 100)).thenReturn(expected);

        List<JobLogContentDO> result = jobLogContentService.pageByLogId("log-001", 2, 100);

        assertEquals(expected, result);
        verify(jobLogContentMapper).selectByLogId("log-001", 100, 100);
    }

    @Test
    @DisplayName("pageByLogId: 第三页 offset=2*size")
    void pageByLogId_thirdPage_offsetDoubleSize() {
        when(jobLogContentMapper.selectByLogId("log-001", 100, 50)).thenReturn(Collections.emptyList());

        List<JobLogContentDO> result = jobLogContentService.pageByLogId("log-001", 3, 50);

        assertEquals(Collections.emptyList(), result);
        verify(jobLogContentMapper).selectByLogId("log-001", 100, 50);
    }

    @Test
    @DisplayName("pageByLogId: page=0 时 offset 兜底为 0（不出现负数）")
    void pageByLogId_zeroPage_offsetClampedToZero() {
        when(jobLogContentMapper.selectByLogId("log-001", 0, 100)).thenReturn(Collections.emptyList());

        jobLogContentService.pageByLogId("log-001", 0, 100);

        verify(jobLogContentMapper).selectByLogId("log-001", 0, 100);
    }

    // ==================== listAfterLine ====================

    @Test
    @DisplayName("listAfterLine: 透传 mapper.selectAfterLine")
    void listAfterLine_delegatesToMapper() {
        List<JobLogContentDO> expected = List.of(buildLine("line-51", 51));
        when(jobLogContentMapper.selectAfterLine("log-001", 50)).thenReturn(expected);

        List<JobLogContentDO> result = jobLogContentService.listAfterLine("log-001", 50);

        assertEquals(expected, result);
        verify(jobLogContentMapper).selectAfterLine("log-001", 50);
    }

    @Test
    @DisplayName("listAfterLine: fromLineNo=0 返回全部行")
    void listAfterLine_fromZero_returnsAll() {
        List<JobLogContentDO> expected = List.of(buildLine("line-1", 1));
        when(jobLogContentMapper.selectAfterLine("log-001", 0)).thenReturn(expected);

        List<JobLogContentDO> result = jobLogContentService.listAfterLine("log-001", 0);

        assertEquals(expected, result);
        verify(jobLogContentMapper).selectAfterLine("log-001", 0);
    }

    // ==================== countByLogId ====================

    @Test
    @DisplayName("countByLogId: 透传 mapper.countByLogId")
    void countByLogId_delegatesToMapper() {
        when(jobLogContentMapper.countByLogId("log-001")).thenReturn(42);

        int count = jobLogContentService.countByLogId("log-001");

        assertEquals(42, count);
        verify(jobLogContentMapper).countByLogId("log-001");
    }

    @Test
    @DisplayName("countByLogId: 无记录返回 0")
    void countByLogId_noRecords_returnsZero() {
        when(jobLogContentMapper.countByLogId("log-empty")).thenReturn(0);

        int count = jobLogContentService.countByLogId("log-empty");

        assertEquals(0, count);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建测试用日志行实体。
     */
    private JobLogContentDO buildLine(String content, int lineNo) {
        JobLogContentDO line = new JobLogContentDO();
        line.setId("id-" + lineNo);
        line.setLogId("log-001");
        line.setJobKey("demo-job");
        line.setLineNo(lineNo);
        line.setLogLevel("INFO");
        line.setContent(content);
        line.setDeleted(0);
        return line;
    }
}

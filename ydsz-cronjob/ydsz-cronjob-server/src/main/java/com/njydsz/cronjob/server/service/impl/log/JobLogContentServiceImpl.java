package com.njydsz.cronjob.server.service.impl.log;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.infra.mapper.log.JobLogContentMapper;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务日志内容服务实现。
 *
 * <p>拆分存储任务执行日志的内容（{@code ydsz_job_log_content}），
 * 与 {@code ydsz_job_log} 1:1 关联，解决大字段导致的 IO 性能问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogContentServiceImpl implements JobLogContentService {

    /** 任务日志内容 Mapper（分页/增量查询） */
    private final JobLogContentMapper jobLogContentMapper;

    @Override
    public void batchSave(List<JobLogContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        for (JobLogContent content : contents) {
            jobLogContentMapper.insert(content);
        }
    }

    @Override
    public List<JobLogContent> pageByLogId(String logId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return jobLogContentMapper.selectByLogId(logId, offset, size);
    }

    @Override
    public List<JobLogContent> listAfterLine(String logId, int fromLineNo) {
        return jobLogContentMapper.selectAfterLine(logId, fromLineNo);
    }

    @Override
    public int countByLogId(String logId) {
        return jobLogContentMapper.countByLogId(logId);
    }

    @Override
    public List<JobLogContent> searchByKeyword(String logId, String keyword, int page, int size) {
        if (logId == null || logId.isBlank() || keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        int offset = Math.max(0, (page - 1) * size);
        return jobLogContentMapper.selectByLogIdAndKeyword(logId, keyword, offset, size);
    }
}

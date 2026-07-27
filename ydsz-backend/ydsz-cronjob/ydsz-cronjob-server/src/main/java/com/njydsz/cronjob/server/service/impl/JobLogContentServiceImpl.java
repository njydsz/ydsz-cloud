package com.njydsz.cronjob.server.service.impl.log;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.infra.mapper.log.JobLogContentMapper;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
/**
 * 任务日志内容 Service 实现（P0-2 在线日志白屏化）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@code batchSave}: 循环 insert 批量写入；空列表直接返回，避免无意义 DB 调用</li>
 *   <li>{@code pageByLogId}: 计算 offset = (page-1)*size，调用 mapper.selectByLogId</li>
 *   <li>{@code listAfterLine}: 透传 mapper.selectAfterLine，供 SSE 增量推送</li>
 *   <li>{@code countByLogId}: 透传 mapper.countByLogId</li>
 * </ul>
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

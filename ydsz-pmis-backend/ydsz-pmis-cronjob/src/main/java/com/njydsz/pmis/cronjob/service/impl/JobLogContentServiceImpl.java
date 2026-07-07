package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.cronjob.entity.JobLogContentDO;
import com.njydsz.pmis.cronjob.mapper.JobLogContentMapper;
import com.njydsz.pmis.cronjob.service.JobLogContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogContentServiceImpl implements JobLogContentService {

    private final JobLogContentMapper jobLogContentMapper;

    @Override
    public void batchSave(List<JobLogContentDO> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        for (JobLogContentDO content : contents) {
            jobLogContentMapper.insert(content);
        }
    }

    @Override
    public List<JobLogContentDO> pageByLogId(String logId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return jobLogContentMapper.selectByLogId(logId, offset, size);
    }

    @Override
    public List<JobLogContentDO> listAfterLine(String logId, int fromLineNo) {
        return jobLogContentMapper.selectAfterLine(logId, fromLineNo);
    }

    @Override
    public int countByLogId(String logId) {
        return jobLogContentMapper.countByLogId(logId);
    }
}

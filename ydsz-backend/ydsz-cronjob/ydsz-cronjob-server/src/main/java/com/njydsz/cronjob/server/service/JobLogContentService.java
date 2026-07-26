package com.njydsz.cronjob.server.service.log;

import java.util.List;

import com.njydsz.cronjob.domain.entity.log.JobLogContentDO;

/**
 * 任务日志内容 Service（P0-2 在线日志白屏化）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogContentService {

    /**
     * 批量写入日志行（异步落库）。
     *
     * @param contents 日志行列表
     */
    void batchSave(List<JobLogContentDO> contents);

    /**
     * 分页查询指定执行日志的明细内容。
     *
     * @param logId  执行日志 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 日志行列表
     */
    List<JobLogContentDO> pageByLogId(String logId, int page, int size);

    /**
     * 查询指定行号之后的日志行（SSE 增量推送用）。
     *
     * @param logId      执行日志 ID
     * @param fromLineNo 起始行号（不含）
     * @return 日志行列表
     */
    List<JobLogContentDO> listAfterLine(String logId, int fromLineNo);

    /**
     * 统计指定执行日志的总行数。
     *
     * @param logId 执行日志 ID
     * @return 总行数
     */
    int countByLogId(String logId);

    /**
     * P1-9: 关键字搜索日志内容。
     *
     * <p>在指定执行日志的内容中搜索包含关键字的行，支持大小写不敏感匹配。
     * 用于快速定位错误信息或关键变量输出。
     *
     * @param logId   执行日志 ID
     * @param keyword 搜索关键词
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @return 匹配的日志行列表
     */
    List<JobLogContentDO> searchByKeyword(String logId, String keyword, int page, int size);
}

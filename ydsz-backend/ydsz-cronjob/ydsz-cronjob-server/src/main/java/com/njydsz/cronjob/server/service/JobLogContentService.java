package com.njydsz.cronjob.server.service.log;

import java.util.List;

import com.njydsz.cronjob.domain.entity.log.JobLogContent;

/**
 * 任务执行日志内容 Service
 *
 * <p>实现"在线日志白屏化"——任务执行的每一行 stdout/stderr 单独存储,支持
 * 实时推送(SSE)、分页查询、关键字搜索。前端无需登录服务器即可在线查看任务执行细节。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>写入</b>：{@link #batchSave} — 异步批量落库,避免频繁 IO</li>
 *   <li><b>分页查询</b>：{@link #pageByLogId} — 详情页滚动加载</li>
 *   <li><b>增量查询</b>：{@link #listAfterLine} — SSE 实时推送,只取新行</li>
 *   <li><b>总行数</b>：{@link #countByLogId} — 详情页总条数</li>
 *   <li><b>搜索</b>：{@link #searchByKeyword} — 大小写不敏感的关键字搜索</li>
 * </ul>
 *
 * <p><b>存储：</b>日志行写入 {@code ydsz_job_log_content} 表,每行含
 * {@code logId / lineNo / content / logTime} 字段。
 *
 * <p><b>SSE 协议：</b>前端订阅 {@code /cronjob/log/{logId}/stream},服务端每 1s 推送
 * {@code listAfterLine(logId, lastLineNo)} 的新行,实现"实时滚动"效果。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.cronjob.domain.entity.log.JobLogContent 日志行实体
 * @see JobService 任务 Service(执行时调用 batchSave 记录输出)
 */
public interface JobLogContentService {

    /**
     * 批量写入日志行（异步落库）。
     *
     * @param contents 日志行列表
     */
    void batchSave(List<JobLogContent> contents);

    /**
     * 分页查询指定执行日志的明细内容。
     *
     * @param logId  执行日志 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 日志行列表
     */
    List<JobLogContent> pageByLogId(String logId, int page, int size);

    /**
     * 查询指定行号之后的日志行（SSE 增量推送用）。
     *
     * @param logId      执行日志 ID
     * @param fromLineNo 起始行号（不含）
     * @return 日志行列表
     */
    List<JobLogContent> listAfterLine(String logId, int fromLineNo);

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
    List<JobLogContent> searchByKeyword(String logId, String keyword, int page, int size);
}

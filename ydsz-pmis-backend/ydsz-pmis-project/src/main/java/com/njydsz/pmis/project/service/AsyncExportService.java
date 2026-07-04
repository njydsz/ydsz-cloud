package com.njydsz.pmis.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * 异步导出服务接口。
 *
 * <p>提供大文件后台异步导出能力，配合下载中心使用：
 * <ol>
 *   <li>前端提交导出任务 → PENDING 入库</li>
 *   <li>定时 Job 拉取 PENDING 任务生成文件 → COMPLETED</li>
 *   <li>前端轮询下载中心获取下载 URL</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AsyncExportService {

    /**
     * pmis_export_record 表的 created_at 列名（Java Bean 字段命名）。
     *
     * <p>由于 {@link #getExportRecords(Long, Pageable)} 返回 {@code Map<String, Object>}
     * 投影而非实体类，无法使用方法引用推导字段名；这里将列名集中到常量，控制器层与
     * Service 层共用同一字符串常量，避免硬编码导致的字段名失同步。
     */
    String COL_CREATED_AT = "createdAt";

    /**
     * 提交异步导出任务。
     *
     * @param userId     用户ID
     * @param exportType 导出类型
     * @param params     导出参数
     * @return 导出记录ID
     */
    Long submitExport(Long userId, String exportType, Map<String, Object> params);

    /**
     * 查询导出记录列表。
     *
     * @param userId   用户ID
     * @param pageable 分页参数
     * @return 导出记录分页
     */
    Page<Map<String, Object>> getExportRecords(Long userId, Pageable pageable);

    /**
     * 获取导出文件下载 URL。
     *
     * @param recordId 导出记录ID
     * @return 下载 URL，记录不存在或未完成时返回 null
     */
    String getDownloadUrl(Long recordId);

    /**
     * 删除导出记录。
     *
     * @param recordId 导出记录ID
     */
    void deleteExportRecord(Long recordId);

    /**
     * 执行异步导出（由 Job 调用）。
     *
     * @param recordId 导出记录ID
     */
    void executeExport(Long recordId);
}

package com.njydsz.pmis.cronjob.service.schedule;

import com.njydsz.pmis.cronjob.entity.schedule.GlueCodeDO;

import java.util.List;

/**
 * GLUE 在线编码服务（P1-2 GLUE 在线编码）。
 *
 * <p>提供 GLUE 代码的版本管理能力：保存新版本、查询最新版本、查询版本列表、
 * 按版本回滚。回滚操作本身会创建一个新版本（内容为目标版本代码），
 * 保留完整版本历史便于审计与再次回滚。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface GlueCodeService {

    /**
     * 保存新版本 GLUE 代码。
     *
     * <p>版本号自动递增（max(version)+1），首次保存版本号为 1。
     *
     * @param jobId      任务 ID
     * @param sourceCode 源代码内容
     * @param language   语言（GROOVY / JAVA），为空时默认 GROOVY
     * @param remark     版本备注（可空）
     * @return 新创建的 GLUE 代码版本
     */
    GlueCodeDO save(String jobId, String sourceCode, String language, String remark);

    /**
     * 获取指定任务的最新版本 GLUE 代码。
     *
     * @param jobId 任务 ID
     * @return 最新版本 GLUE 代码；不存在时返回 null
     */
    GlueCodeDO getLatest(String jobId);

    /**
     * 获取指定任务的全部版本列表（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 版本列表；无记录时返回空列表
     */
    List<GlueCodeDO> listVersions(String jobId);

    /**
     * 回滚到指定版本。
     *
     * <p>创建一个新版本（version=max+1），内容为目标版本的源代码，
     * 保留原版本历史不修改。
     *
     * @param jobId   任务 ID
     * @param version 目标版本号
     * @return 新创建的回滚版本
     */
    GlueCodeDO rollback(String jobId, Integer version);
}

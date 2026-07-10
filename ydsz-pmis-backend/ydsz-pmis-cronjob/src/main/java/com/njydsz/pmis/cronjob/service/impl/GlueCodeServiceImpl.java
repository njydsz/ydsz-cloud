package com.njydsz.pmis.cronjob.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.entity.GlueCodeDO;
import com.njydsz.pmis.cronjob.mapper.GlueCodeMapper;
import com.njydsz.pmis.cronjob.service.GlueCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * GLUE 在线编码服务实现（P1-2 GLUE 在线编码）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@code save}: 查询当前最大版本号，version+1 后插入新记录</li>
 *   <li>{@code getLatest}: 透传 mapper.selectLatestByJobId</li>
 *   <li>{@code listVersions}: LambdaQueryWrapper 按 version 降序查询</li>
 *   <li>{@code rollback}: 查询目标版本代码，创建新版本（version=max+1）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlueCodeServiceImpl implements GlueCodeService {

    /** GLUE 代码 Mapper（版本化源码 CRUD） */
    private final GlueCodeMapper glueCodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlueCodeDO save(String jobId, String sourceCode, String language, String remark) {
        if (!StringUtils.hasText(jobId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_glue_job_id_required");
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_glue_source_required");
        }
        // 计算新版本号
        GlueCodeDO latest = glueCodeMapper.selectLatestByJobId(jobId);
        int nextVersion = latest == null ? 1 : (latest.getVersion() == null ? 1 : latest.getVersion() + 1);

        GlueCodeDO entity = new GlueCodeDO();
        entity.setJobId(jobId);
        entity.setSourceCode(sourceCode);
        entity.setLanguage(StringUtils.hasText(language) ? language : "GROOVY");
        entity.setVersion(nextVersion);
        entity.setRemark(remark);
        glueCodeMapper.insert(entity);
        log.info("[Glue] 保存 GLUE 代码: jobId={} version={} remark={}", jobId, nextVersion, remark);
        return entity;
    }

    @Override
    public GlueCodeDO getLatest(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return null;
        }
        return glueCodeMapper.selectLatestByJobId(jobId);
    }

    @Override
    public List<GlueCodeDO> listVersions(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<GlueCodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlueCodeDO::getJobId, jobId)
                .orderByDesc(GlueCodeDO::getVersion);
        return glueCodeMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlueCodeDO rollback(String jobId, Integer version) {
        if (!StringUtils.hasText(jobId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_glue_job_id_required");
        }
        if (version == null || version < 1) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_glue_version_invalid");
        }
        // 查询目标版本
        LambdaQueryWrapper<GlueCodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlueCodeDO::getJobId, jobId)
                .eq(GlueCodeDO::getVersion, version);
        GlueCodeDO target = glueCodeMapper.selectOne(wrapper);
        if (target == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_glue_version_not_found");
        }
        // 创建新版本（内容为目标版本代码）
        GlueCodeDO latest = glueCodeMapper.selectLatestByJobId(jobId);
        int nextVersion = latest == null ? 1 : (latest.getVersion() == null ? 1 : latest.getVersion() + 1);

        GlueCodeDO entity = new GlueCodeDO();
        entity.setJobId(jobId);
        entity.setSourceCode(target.getSourceCode());
        entity.setLanguage(target.getLanguage());
        entity.setVersion(nextVersion);
        entity.setRemark("rollback to v" + version);
        glueCodeMapper.insert(entity);
        log.info("[Glue] 回滚 GLUE 代码: jobId={} fromVersion={} toNewVersion={}", jobId, version, nextVersion);
        return entity;
    }
}

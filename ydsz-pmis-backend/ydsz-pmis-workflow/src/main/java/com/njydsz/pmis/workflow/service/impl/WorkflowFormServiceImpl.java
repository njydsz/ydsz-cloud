package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.WorkflowFormDO;
import com.njydsz.pmis.workflow.mapper.WorkflowFormMapper;
import com.njydsz.pmis.workflow.service.WorkflowFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流程表单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowFormServiceImpl implements WorkflowFormService {

    private final WorkflowFormMapper formMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkflowFormDO form) {
        if (!StringUtils.hasText(form.getFormKey())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "formKey 不能为空");
        }
        WorkflowFormDO exists = formMapper.selectByFormKey(form.getFormKey());
        if (exists != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "formKey 已存在: " + form.getFormKey());
        }
        if (form.getVersion() == null) {
            form.setVersion(1);
        }
        if (form.getStatus() == null) {
            form.setStatus("ENABLED");
        }
        if (form.getTenantId() == null) {
            form.setTenantId(1L);
        }
        formMapper.insert(form);
        log.info("[WorkflowForm] 创建表单: key={} name={}", form.getFormKey(), form.getFormName());
        return form.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(WorkflowFormDO form) {
        if (form.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "表单 ID 不能为空");
        }
        WorkflowFormDO exists = formMapper.selectById(form.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "表单不存在");
        }
        // 不允许修改 formKey 唯一键
        if (StringUtils.hasText(form.getFormKey())
                && !form.getFormKey().equals(exists.getFormKey())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "不允许修改 formKey");
        }
        form.setFormKey(null);
        BeanUtils.copyProperties(form, exists, "formKey", "createBy", "createTime");
        formMapper.updateById(exists);
    }

    @Override
    public void delete(Long id) {
        WorkflowFormDO exists = formMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "表单不存在");
        }
        formMapper.deleteById(id);
    }

    @Override
    public WorkflowFormDO getById(Long id) {
        WorkflowFormDO f = formMapper.selectById(id);
        if (f == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "表单不存在");
        }
        return f;
    }

    @Override
    public WorkflowFormDO getByFormKey(String formKey) {
        return formMapper.selectByFormKey(formKey);
    }

    @Override
    public List<WorkflowFormDO> listByProcessKey(String processKey) {
        return formMapper.selectByProcessKey(processKey);
    }

    @Override
    public Page<WorkflowFormDO> page(int page, int size, String keyword, String processKey, String status) {
        Page<WorkflowFormDO> p = new Page<>(page, size);
        LambdaQueryWrapper<WorkflowFormDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(WorkflowFormDO::getFormKey, keyword)
                    .or().like(WorkflowFormDO::getFormName, keyword)
                    .or().like(WorkflowFormDO::getDescription, keyword));
        }
        if (StringUtils.hasText(processKey)) {
            w.eq(WorkflowFormDO::getProcessKey, processKey);
        }
        if (StringUtils.hasText(status)) {
            w.eq(WorkflowFormDO::getStatus, status);
        }
        w.orderByDesc(WorkflowFormDO::getVersion);
        return formMapper.selectPage(p, w);
    }
}

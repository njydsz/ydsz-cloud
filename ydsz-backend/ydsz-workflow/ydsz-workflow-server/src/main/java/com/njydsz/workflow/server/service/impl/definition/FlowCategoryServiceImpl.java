package com.njydsz.workflow.server.service.impl.definition;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.domain.entity.FlowCategory;
import com.njydsz.workflow.domain.entity.FlowDefinition;
import com.njydsz.workflow.infra.mapper.FlowCategoryMapper;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;
import com.njydsz.workflow.server.service.FlowCategoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程分类服务实现
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCategoryServiceImpl implements FlowCategoryService {

    /** 流程分类 Mapper，用于分类的增删改查 */
    private final FlowCategoryMapper categoryMapper;
    /** 流程定义 Mapper，删除分类前校验是否有关联的流程定义 */
    private final FlowDefinitionMapper definitionMapper;

    @Override
    public List<FlowCategory> listAll(String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        List<FlowCategory> list = categoryMapper.selectList(
                new LambdaQueryWrapper<FlowCategory>()
                        .eq(FlowCategory::getTenantId, tid)
                        .eq(FlowCategory::getDeleted, 0)
        );
        list.sort(Comparator.comparingInt(c ->
                c.getSortNum() == null ? 0 : c.getSortNum()));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowCategoryDTO dto, String tenantId) {
        // 校验编码唯一
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<FlowCategory>()
                        .eq(FlowCategory::getCategoryCode, dto.getCategoryCode())
                        .eq(FlowCategory::getTenantId, tid)
                        .eq(FlowCategory::getDeleted, 0)
        );
        if (count != null && count > 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_category_code_exists", dto.getCategoryCode());
        }

        FlowCategory category = new FlowCategory();
        category.setCategoryCode(dto.getCategoryCode());
        category.setCategoryName(dto.getCategoryName());
        category.setParentId(dto.getParentId());
        category.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
        category.setIcon(dto.getIcon());
        category.setRemark(dto.getRemark());
        category.setTenantId(tid);
        categoryMapper.insert(category);
        log.info("[FlowCategory] 新增分类: code={} name={} id={}",
                category.getCategoryCode(), category.getCategoryName(), category.getId());
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlowCategoryDTO dto) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_id_required");
        }
        FlowCategory existing = categoryMapper.selectById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.workflow.msg_6541ab08", dto.getId());
        }
        existing.setCategoryName(dto.getCategoryName());
        if (dto.getParentId() != null) {
            existing.setParentId(dto.getParentId());
        }
        if (dto.getSortNum() != null) {
            existing.setSortNum(dto.getSortNum());
        }
        if (dto.getIcon() != null) {
            existing.setIcon(dto.getIcon());
        }
        if (dto.getRemark() != null) {
            existing.setRemark(dto.getRemark());
        }
        categoryMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        FlowCategory existing = categoryMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            return;
        }
        // 校验是否有子分类
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<FlowCategory>()
                        .eq(FlowCategory::getParentId, id)
                        .eq(FlowCategory::getDeleted, 0)
        );
        if (childCount != null && childCount > 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_category_has_children");
        }
        // 校验是否有关联的流程定义
        Long defCount = definitionMapper.selectCount(
                new LambdaQueryWrapper<FlowDefinition>()
                        .eq(FlowDefinition::getCategory, id)
                        .eq(FlowDefinition::getDeleted, 0)
        );
        if (defCount != null && defCount > 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_category_has_definitions");
        }
        existing.setDeleted(1);
        categoryMapper.updateById(existing);
    }
}

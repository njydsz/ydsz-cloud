package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.execution.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.execution.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.execution.engine.StageGateValidator;
import com.njydsz.pmis.execution.entity.DeliveryItemDO;
import com.njydsz.pmis.execution.entity.DeliveryStandardDO;
import com.njydsz.pmis.execution.enums.DeliveryItemStatus;
import com.njydsz.pmis.execution.enums.DeliveryStage;
import com.njydsz.pmis.execution.enums.ProjectType;
import com.njydsz.pmis.execution.mapper.DeliveryItemMapper;
import com.njydsz.pmis.execution.mapper.DeliveryStandardMapper;
import com.njydsz.pmis.execution.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 交付物服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryStandardMapper standardMapper;
    private final DeliveryItemMapper itemMapper;

    // ========== 标准管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStandard(DeliveryStandardCreateDTO dto) {
        validateStandard(dto);
        DeliveryStandardDO s = new DeliveryStandardDO();
        BeanUtils.copyProperties(dto, s);
        if (s.getRequired() == null) s.setRequired(1);
        if (s.getTriggerTr() == null) s.setTriggerTr(0);
        if (s.getTenantId() == null) s.setTenantId(1L);
        if (s.getProviderTraceId() == null) s.setProviderTraceId("");
        standardMapper.insert(s);
        log.info("[DeliveryStandard] 创建交付物标准: type={} stage={} name={}",
                s.getProjectType(), s.getStage(), s.getDeliveryName());
        return s.getId();
    }

    @Override
    public void deleteStandard(Long id) {
        DeliveryStandardDO s = standardMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "交付物标准不存在");
        }
        standardMapper.deleteById(id);
    }

    @Override
    public DeliveryStandardDO getStandardById(Long id) {
        DeliveryStandardDO s = standardMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "交付物标准不存在");
        }
        return s;
    }

    @Override
    public List<DeliveryStandardDO> listStandards(String projectType, String projectLevel, String stage) {
        if (StringUtils.hasText(stage)) {
            return standardMapper.selectByStage(projectType, projectLevel, stage);
        }
        return standardMapper.selectByTypeAndLevel(projectType, projectLevel);
    }

    @Override
    public long countStandardsByType(String projectType) {
        if (!StringUtils.hasText(projectType)) return 0L;
        return standardMapper.countByType(projectType);
    }

    // ========== 实例管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(DeliveryItemCreateDTO dto) {
        validateItem(dto);
        if (itemMapper.selectByCode(dto.getItemCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "交付物编码已存在: " + dto.getItemCode());
        }
        DeliveryItemDO i = new DeliveryItemDO();
        BeanUtils.copyProperties(dto, i);
        if (i.getRequired() == null) i.setRequired(1);
        if (i.getTrRequired() == null) i.setTrRequired(0);
        if (i.getTrCompleted() == null) i.setTrCompleted(0);
        if (!StringUtils.hasText(i.getStatus())) {
            i.setStatus(DeliveryItemStatus.PENDING.getCode());
        }
        if (i.getTenantId() == null) i.setTenantId(1L);
        if (i.getProviderTraceId() == null) i.setProviderTraceId("");
        itemMapper.insert(i);
        log.info("[DeliveryItem] 创建交付物: code={} project={} stage={}",
                i.getItemCode(), i.getInitiationId(), i.getStage());
        return i.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeItemStatus(DeliveryItemStatusDTO dto) {
        DeliveryItemDO i = getItemById(dto.getId());
        DeliveryItemStatus from = DeliveryItemStatus.fromCode(i.getStatus());
        DeliveryItemStatus to = DeliveryItemStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态非法: " + i.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "交付物状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        if (to == DeliveryItemStatus.SUBMITTED && i.getActualSubmitDate() == null) {
            i.setActualSubmitDate(today);
        }
        if (to == DeliveryItemStatus.ACCEPTED) {
            i.setAcceptedDate(today);
        }
        if (StringUtils.hasText(dto.getReviewComment())) i.setReviewComment(dto.getReviewComment());
        if (dto.getReviewerId() != null) i.setReviewerId(dto.getReviewerId());
        if (StringUtils.hasText(dto.getReviewerName())) i.setReviewerName(dto.getReviewerName());
        i.setStatus(to.getCode());
        itemMapper.updateById(i);
        log.info("[DeliveryItem] 状态迁移: id={} {} -> {}", i.getId(), from.getCode(), to.getCode());
    }

    @Override
    public void markTrCompleted(Long itemId, Integer completed) {
        DeliveryItemDO i = getItemById(itemId);
        if (Integer.valueOf(1).equals(i.getTrRequired()) == false) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "该交付物无需 TR 评审");
        }
        itemMapper.updateTrCompleted(itemId, completed);
    }

    @Override
    public void deleteItem(Long id) {
        DeliveryItemDO i = getItemById(id);
        DeliveryItemStatus st = DeliveryItemStatus.fromCode(i.getStatus());
        if (st == DeliveryItemStatus.ACCEPTED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已验收的交付物不能删除");
        }
        itemMapper.deleteById(id);
    }

    @Override
    public DeliveryItemDO getItemById(Long id) {
        DeliveryItemDO i = itemMapper.selectById(id);
        if (i == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "交付物实例不存在");
        }
        return i;
    }

    @Override
    public List<DeliveryItemDO> listItemsByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return itemMapper.selectByInitiation(initiationId);
    }

    @Override
    public List<DeliveryItemDO> listItemsByStage(Long initiationId, String stage) {
        if (initiationId == null) return List.of();
        return itemMapper.selectByStage(initiationId, stage);
    }

    @Override
    public List<Map<String, Object>> aggregateItemStatus(Long initiationId) {
        if (initiationId == null) return List.of();
        return itemMapper.aggregateByStatus(initiationId);
    }

    // ========== 阶段门控 ==========

    @Override
    public StageGateValidator.GateCheckResult checkStageGate(Long initiationId, String targetStage,
                                                              String projectLevel) {
        DeliveryStage target = DeliveryStage.fromCode(targetStage);
        if (target == null) {
            return StageGateValidator.GateCheckResult.fail("未知阶段: " + targetStage);
        }
        List<DeliveryItemDO> items = listItemsByInitiation(initiationId);
        return StageGateValidator.check(initiationId, target, items, projectLevel);
    }

    // ========== 私有方法 ==========

    private void validateStandard(DeliveryStandardCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (ProjectType.fromCode(dto.getProjectType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目类型不合法: " + dto.getProjectType());
        }
        if (DeliveryStage.fromCode(dto.getStage()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "阶段不合法: " + dto.getStage());
        }
    }

    private void validateItem(DeliveryItemCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        if (StringUtils.hasText(dto.getStage())
                && DeliveryStage.fromCode(dto.getStage()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "阶段不合法: " + dto.getStage());
        }
        if (StringUtils.hasText(dto.getProjectType())
                && ProjectType.fromCode(dto.getProjectType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目类型不合法: " + dto.getProjectType());
        }
    }
}

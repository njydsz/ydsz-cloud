package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.project.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.project.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.project.engine.StageGateValidator;
import com.njydsz.pmis.project.entity.DeliveryItemDO;
import com.njydsz.pmis.project.entity.DeliveryStandardDO;
import com.njydsz.pmis.project.enums.DeliveryItemStatus;
import com.njydsz.pmis.project.enums.DeliveryStage;
import com.njydsz.pmis.project.enums.ProjectType;
import com.njydsz.pmis.project.mapper.DeliveryItemMapper;
import com.njydsz.pmis.project.mapper.DeliveryStandardMapper;
import com.njydsz.pmis.project.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
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
        if (s.getTenantId() == null) s.setTenantId(TenantContext.getTenantId());
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
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_ea3dc234");
        }
        standardMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryStandardDO getStandardById(Long id) {
        DeliveryStandardDO s = standardMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_ea3dc234");
        }
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryStandardDO> listStandards(String projectType, String projectLevel, String stage) {
        if (StringUtils.hasText(stage)) {
            return standardMapper.selectByStage(projectType, projectLevel, stage);
        }
        return standardMapper.selectByTypeAndLevel(projectType, projectLevel);
    }

    @Override
    @Transactional(readOnly = true)
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
                    "error.execution.msg_6f4c0a13" + dto.getItemCode());
        }
        DeliveryItemDO i = new DeliveryItemDO();
        BeanUtils.copyProperties(dto, i);
        if (i.getRequired() == null) i.setRequired(1);
        if (i.getTrRequired() == null) i.setTrRequired(0);
        if (i.getTrCompleted() == null) i.setTrCompleted(0);
        if (!StringUtils.hasText(i.getStatus())) {
            i.setStatus(DeliveryItemStatus.PENDING.getCode());
        }
        if (i.getTenantId() == null) i.setTenantId(TenantContext.getTenantId());
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_7bc741c6" + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_2e33226a" + i.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_ba80cf32" + from.getDesc() + " → " + to.getDesc());
        }
        // LocalDateTime now removed - unused
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_f693a197");
        }
        itemMapper.updateTrCompleted(itemId, completed);
    }

    @Override
    public void deleteItem(Long id) {
        DeliveryItemDO i = getItemById(id);
        DeliveryItemStatus st = DeliveryItemStatus.fromCode(i.getStatus());
        if (st == DeliveryItemStatus.ACCEPTED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_dfa7a85a");
        }
        itemMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryItemDO getItemById(Long id) {
        DeliveryItemDO i = itemMapper.selectById(id);
        if (i == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_2bb641ec");
        }
        return i;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryItemDO> listItemsByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return itemMapper.selectByInitiation(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryItemDO> listItemsByStage(Long initiationId, String stage) {
        if (initiationId == null) return List.of();
        return itemMapper.selectByStage(initiationId, stage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateItemStatus(Long initiationId) {
        if (initiationId == null) return List.of();
        return itemMapper.aggregateByStatus(initiationId);
    }

    // ========== 阶段门控 ==========

    @Override
    @Transactional(readOnly = true)
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        if (ProjectType.fromCode(dto.getProjectType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_1942429d" + dto.getProjectType());
        }
        if (DeliveryStage.fromCode(dto.getStage()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_4fbcd36c" + dto.getStage());
        }
    }

    private void validateItem(DeliveryItemCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (StringUtils.hasText(dto.getStage())
                && DeliveryStage.fromCode(dto.getStage()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_4fbcd36c" + dto.getStage());
        }
        if (StringUtils.hasText(dto.getProjectType())
                && ProjectType.fromCode(dto.getProjectType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_1942429d" + dto.getProjectType());
        }
    }
}

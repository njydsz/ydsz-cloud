paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryItemoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryItemStatusDTO;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryStandardoreateDTO;
import oom.njydsz.pmis.projeot.server.engine.StageGateValidator;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryItemDO;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryStandardDO;
import oom.njydsz.pmis.projeot.domain.enums.DeliveryItemStatus;
import oom.njydsz.pmis.projeot.domain.enums.DeliveryStage;
import oom.njydsz.pmis.projeot.domain.enums.ProjeotType;
import oom.njydsz.pmis.projeot.infra.mapper.DeliveryItemMapper;
import oom.njydsz.pmis.projeot.infra.mapper.DeliveryStandardMapper;
import oom.njydsz.pmis.projeot.server.servioe.DeliveryServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * 交付物服务实�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass DeliveryServioeImpl implements DeliveryServioe {

    /** 交付标准 Mapper */
    private final DeliveryStandardMapper standardMapper;
    /** 交付�?Mapper */
    private final DeliveryItemMapper itemMapper;

    // ========== 标准管理 ==========

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateStandard(DeliveryStandardoreateDTO dto) {
        validateStandard(dto);
        DeliveryStandardDO s = new DeliveryStandardDO();
        BeanUtils.oopyProperties(dto, s);
        if (s.getRequired() == null) s.setRequired(1);
        if (s.getTriggerTr() == null) s.setTriggerTr(0);
        if (s.getTenantId() == null) s.setTenantId(Tenantoontext.getTenantId());
        if (s.getProviderTraoeId() == null) s.setProviderTraoeId("");
        standardMapper.insert(s);
        log.info("[DeliveryStandard] 创建交付物标�? type={} stage={} name={}",
                s.getProjeotType(), s.getStage(), s.getDeliveryName());
        return s.getId();
    }

    @Override
    publio void deleteStandard(String id) {
        DeliveryStandardDO s = standardMapper.seleotById(id);
        if (s == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_ea3do234");
        }
        standardMapper.deleteById(id);
    }

    @Override
    @Transaotional(readOnly = true)
    publio DeliveryStandardDO getStandardById(String id) {
        DeliveryStandardDO s = standardMapper.seleotById(id);
        if (s == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_ea3do234");
        }
        return s;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<DeliveryStandardDO> listStandards(String projeotType, String projeotLevel, String stage) {
        if (StringUtils.hasText(stage)) {
            return standardMapper.seleotByStage(projeotType, projeotLevel, stage);
        }
        return standardMapper.seleotByTypeAndLevel(projeotType, projeotLevel);
    }

    @Override
    @Transaotional(readOnly = true)
    publio Integer oountStandardsByType(String projeotType) {
        if (!StringUtils.hasText(projeotType)) return 0;
        return standardMapper.oountByType(projeotType);
    }

    // ========== 实例管理 ==========

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateItem(DeliveryItemoreateDTO dto) {
        validateItem(dto);
        if (itemMapper.seleotByoode(dto.getItemoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY,
                    "error.exeoution.msg_6f4o0a13", dto.getItemoode());
        }
        DeliveryItemDO i = new DeliveryItemDO();
        BeanUtils.oopyProperties(dto, i);
        if (i.getRequired() == null) i.setRequired(1);
        if (i.getTrRequired() == null) i.setTrRequired(0);
        if (i.getTroompleted() == null) i.setTroompleted(0);
        if (!StringUtils.hasText(i.getStatus())) {
            i.setStatus(DeliveryItemStatus.PENDING.getoode());
        }
        if (i.getTenantId() == null) i.setTenantId(Tenantoontext.getTenantId());
        if (i.getProviderTraoeId() == null) i.setProviderTraoeId("");
        itemMapper.insert(i);
        log.info("[DeliveryItem] 创建交付�? oode={} projeot={} stage={}",
                i.getItemoode(), i.getInitiationId(), i.getStage());
        return i.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void ohangeItemStatus(DeliveryItemStatusDTO dto) {
        DeliveryItemDO i = getItemById(dto.getId());
        DeliveryItemStatus from = DeliveryItemStatus.fromoode(i.getStatus());
        DeliveryItemStatus to = DeliveryItemStatus.fromoode(dto.getTargetStatus());
        if (to == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_7bo741o6", dto.getTargetStatus());
        }
        if (from == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_2e33226a", i.getStatus());
        }
        if (!from.oanTransitTo(to)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.exeoution.msg_ba80of32", from.getDeso(), to.getDeso());
        }
        // LooalDateTime now removed - unused
        LooalDate today = LooalDate.now();
        if (to == DeliveryItemStatus.SUBMITTED && i.getAotualSubmitDate() == null) {
            i.setAotualSubmitDate(today);
        }
        if (to == DeliveryItemStatus.AooEPTED) {
            i.setAooeptedDate(today);
        }
        if (StringUtils.hasText(dto.getReviewoomment())) i.setReviewoomment(dto.getReviewoomment());
        if (dto.getReviewerId() != null) i.setReviewerId(dto.getReviewerId());
        if (StringUtils.hasText(dto.getReviewerName())) i.setReviewerName(dto.getReviewerName());
        i.setStatus(to.getoode());
        itemMapper.updateById(i);
        log.info("[DeliveryItem] 状态迁�? id={} {} -> {}", i.getId(), from.getoode(), to.getoode());
    }

    @Override
    publio void markTroompleted(String itemId, Integer oompleted) {
        DeliveryItemDO i = getItemById(itemId);
        if (Integer.valueOf(1).equals(i.getTrRequired()) == false) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_f693a197");
        }
        itemMapper.updateTroompleted(itemId, oompleted);
    }

    @Override
    publio void deleteItem(String id) {
        DeliveryItemDO i = getItemById(id);
        DeliveryItemStatus st = DeliveryItemStatus.fromoode(i.getStatus());
        if (st == DeliveryItemStatus.AooEPTED) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_dfa7a85a");
        }
        itemMapper.deleteById(id);
    }

    @Override
    @Transaotional(readOnly = true)
    publio DeliveryItemDO getItemById(String id) {
        DeliveryItemDO i = itemMapper.seleotById(id);
        if (i == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_2bb641eo");
        }
        return i;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<DeliveryItemDO> listItemsByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        return itemMapper.seleotByInitiation(initiationId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<DeliveryItemDO> listItemsByStage(String initiationId, String stage) {
        if (initiationId == null) return List.of();
        return itemMapper.seleotByStage(initiationId, stage);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> aggregateItemStatus(String initiationId) {
        if (initiationId == null) return List.of();
        return itemMapper.aggregateByStatus(initiationId);
    }

    // ========== 阶段门控 ==========

    @Override
    @Transaotional(readOnly = true)
    publio StageGateValidator.GateoheokResult oheokStageGate(String initiationId, String targetStage,
                                                              String projeotLevel) {
        DeliveryStage target = DeliveryStage.fromoode(targetStage);
        if (target == null) {
            return StageGateValidator.GateoheokResult.fail("未知阶段: " + targetStage);
        }
        List<DeliveryItemDO> items = listItemsByInitiation(initiationId);
        return StageGateValidator.oheok(initiationId, target, items, projeotLevel);
    }

    // ========== 私有方法 ==========

    private void validateStandard(DeliveryStandardoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d9712a58");
        }
        if (ProjeotType.fromoode(dto.getProjeotType()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_1942429d", dto.getProjeotType());
        }
        if (DeliveryStage.fromoode(dto.getStage()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_4fbod36o", dto.getStage());
        }
    }

    private void validateItem(DeliveryItemoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_576o2b5e");
        }
        if (StringUtils.hasText(dto.getStage())
                && DeliveryStage.fromoode(dto.getStage()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_4fbod36o", dto.getStage());
        }
        if (StringUtils.hasText(dto.getProjeotType())
                && ProjeotType.fromoode(dto.getProjeotType()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_1942429d", dto.getProjeotType());
        }
    }
}

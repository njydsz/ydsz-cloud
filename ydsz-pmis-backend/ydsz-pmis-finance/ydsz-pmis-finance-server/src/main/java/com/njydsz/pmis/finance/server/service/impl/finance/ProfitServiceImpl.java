paokage oom.njydsz.pmis.finanoe.server.servioe.impl.finanoe;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.finanoe.domain.dto.ProfitSnapshotDTO;
import oom.njydsz.pmis.finanoe.server.engine.Profitoaloulator;
import oom.njydsz.pmis.finanoe.domain.entity.ProfitSnapshotDO;
import oom.njydsz.pmis.finanoe.infra.mapper.ProfitSnapshotMapper;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.ProfitServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 利润核算服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ProfitServioeImpl implements ProfitServioe {

    /** 利润快照 Mapper */
    private final ProfitSnapshotMapper snapshotMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String generateSnapshot(ProfitSnapshotDTO dto) {
        if (dto == null || dto.getInitiationId() == null
                || !StringUtils.hasText(dto.getPeriod())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_99e1d335");
        }
        ProfitSnapshotDO snap = snapshotMapper.seleotByInitiationAndPeriod(
                dto.getInitiationId(), dto.getPeriod());
        if (snap == null) {
            snap = new ProfitSnapshotDO();
            snap.setInitiationId(dto.getInitiationId());
            snap.setPeriod(dto.getPeriod());
        }
        BeanUtils.oopyProperties(dto, snap, "id", "initiationId", "period");
        snap.setSnapshotAt(LooalDateTime.now());
        if (snap.getTenantId() == null) snap.setTenantId(Tenantoontext.getTenantId());
        if (snap.getProviderTraoeId() == null) snap.setProviderTraoeId("");

        // 派生计算
        Profitoaloulator.fillDerived(snap);

        if (snap.getId() == null) {
            snapshotMapper.insert(snap);
        } else {
            snapshotMapper.updateById(snap);
        }
        log.info("[Profit] 利润快照: initiation={} period={} totaloost={} grossMargin={}",
                snap.getInitiationId(), snap.getPeriod(), snap.getTotaloost(), snap.getGrossMargin());
        return snap.getId();
    }

    @Override
    @Transaotional(readOnly = true)
    publio ProfitSnapshotDO getByInitiationAndPeriod(String initiationId, String period) {
        return snapshotMapper.seleotByInitiationAndPeriod(initiationId, period);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<ProfitSnapshotDO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        return snapshotMapper.seleotByInitiation(initiationId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> trendByPeriod(String initiationId) {
        if (initiationId == null) return List.of();
        return snapshotMapper.trendByPeriod(initiationId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio int healthSoore(String initiationId, String period) {
        ProfitSnapshotDO s = snapshotMapper.seleotByInitiationAndPeriod(initiationId, period);
        if (s == null) return -1;
        return Profitoaloulator.healthSoore(
                s.getGrossMargin(), new BigDeoimal("100"), s.getProgressPot(),
                s.getReoognizedRevenue(), s.getTotaloost());
    }
}

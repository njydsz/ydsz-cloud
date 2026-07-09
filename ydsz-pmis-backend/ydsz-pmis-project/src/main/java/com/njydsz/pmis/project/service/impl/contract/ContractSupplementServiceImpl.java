package com.njydsz.pmis.project.service.impl.contract;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.contract.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.contract.ContractDO;
import com.njydsz.pmis.project.entity.contract.ContractSupplementDO;
import com.njydsz.pmis.project.mapper.contract.ContractMapper;
import com.njydsz.pmis.project.mapper.contract.ContractSupplementMapper;
import com.njydsz.pmis.project.service.contract.ContractSupplementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 合同补充协议服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractSupplementServiceImpl implements ContractSupplementService {

    /** 允许的补充协议类型集合：金额/范围/期限/其他 */
    private static final Set<String> TYPES = Set.of("AMOUNT", "SCOPE", "TERM", "OTHER");

    /** 补充协议 Mapper */
    private final ContractSupplementMapper supplementMapper;
    /** 合同 Mapper（用于校验合同存在性并联动主合同金额） */
    private final ContractMapper contractMapper;

    /**
     * 创建合同补充协议。
     * <p>处理流程：参数校验 → 合同存在性校验 → 编号唯一性预检 → 属性拷贝 →
     * 默认状态 DRAFT → 持久化。金额类型(AMOUNT)且 changeAmount 非零时，
     * 自动联动调整主合同 totalAmount 并回填 newTotalAmount。</p>
     *
     * @param dto 补充协议参数
     * @return 补充协议 ID
     * @throws BizException 合同不存在、编号重复或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ContractSupplementDTO dto) {
        validate(dto);
        if (contractMapper.selectById(dto.getContractId()) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_22d39b90");
        }
        if (supplementMapper.selectByCode(dto.getSupplementCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.project.msg_3592a4cc");
        }
        ContractSupplementDO s = new ContractSupplementDO();
        BeanUtils.copyProperties(dto, s);
        if (!StringUtils.hasText(s.getStatus())) s.setStatus("DRAFT");
        if (s.getTenantId() == null) s.setTenantId(TenantContext.getTenantId());
        supplementMapper.insert(s);

        // 联动：金额类型补充协议直接调整主合同金额
        if ("AMOUNT".equalsIgnoreCase(s.getSupplementType())
                && s.getChangeAmount() != null
                && s.getChangeAmount().signum() != 0) {
            contractMapper.adjustTotalAmount(dto.getContractId(), s.getChangeAmount());
            ContractDO refreshed = contractMapper.selectById(dto.getContractId());
            if (refreshed != null) {
                s.setNewTotalAmount(refreshed.getTotalAmount());
                supplementMapper.updateById(s);
            }
            log.info("[Supplement] 调整主合同 {} 金额 delta={}",
                    dto.getContractId(), s.getChangeAmount());
        }
        log.info("[Supplement] 创建补充协议: code={} type={}", s.getSupplementCode(), s.getSupplementType());
        return s.getId();
    }

    /**
     * 删除补充协议（按主键）。
     *
     * @param id 补充协议 ID
     * @throws BizException 补充协议不存在时抛出
     */
    @Override
    public void delete(String id) {
        ContractSupplementDO s = supplementMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_163e0077");
        }
        supplementMapper.deleteById(id);
    }

    /**
     * 根据主键查询补充协议详情。
     *
     * @param id 补充协议 ID
     * @return 补充协议实体
     * @throws BizException 补充协议不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public ContractSupplementDO getById(String id) {
        ContractSupplementDO s = supplementMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_163e0077");
        }
        return s;
    }

    /**
     * 按合同查询补充协议列表。
     *
     * @param contractId 合同 ID
     * @return 补充协议列表，合同 ID 为空时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ContractSupplementDO> listByContract(String contractId) {
        if (contractId == null) return List.of();
        return supplementMapper.selectByContractId(contractId);
    }

    /**
     * 分页查询补充协议，按创建时间倒序。
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param contractId 合同 ID，可空
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ContractSupplementDO> page(int page, int size, String contractId) {
        Page<ContractSupplementDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractSupplementDO> w = new LambdaQueryWrapper<>();
        if (contractId != null) w.eq(ContractSupplementDO::getContractId, contractId);
        w.orderByDesc(ContractSupplementDO::getCreatedAt);
        return supplementMapper.selectPage(p, w);
    }

    /**
     * 校验补充协议参数。
     *
     * @param dto 补充协议参数
     * @throws BizException 参数为空、合同 ID 缺失、编号/名称缺失或类型非法时抛出
     */
    private void validate(ContractSupplementDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getContractId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_af96cf73");
        }
        if (!StringUtils.hasText(dto.getSupplementCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_9b9ada20");
        }
        if (!StringUtils.hasText(dto.getSupplementName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_33d967a0");
        }
        if (!TYPES.contains(dto.getSupplementType().toUpperCase())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_3820d28c", dto.getSupplementType());
        }
    }
}

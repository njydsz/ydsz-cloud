package com.njydsz.literule.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.dto.RuleDefinition;
import com.njydsz.literule.domain.dto.RuleVersionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.repository.RuleVersionRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.infra.converter.LiteruleConverter;
import com.njydsz.literule.infra.entity.RuleDefinitionDTO;
import com.njydsz.literule.infra.entity.RuleVersionHistory;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;
import com.njydsz.literule.infra.mapper.RuleVersionHistoryMapper;

/**
 * 规则版本仓储实现（Infra 层）。
 *
 * <p>实现 {@link RuleVersionRepository} 接口，封装 {@link RuleVersionHistoryMapper} 与 {@link RuleDefinitionMapper}
 * 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link LiteruleConverter} 将 Entity 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link LiteruleConverter} 转换为 Entity 后执行数据库操作
 *   <li>回滚操作需在同一事务内完成：备份当前版本 → 恢复目标版本 → 新增回滚版本记录
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RuleVersionRepositoryImpl implements RuleVersionRepository {

  private final RuleVersionHistoryMapper ruleVersionHistoryMapper;

  private final RuleDefinitionMapper ruleDefinitionMapper;

  private final LiteruleConverter converter = LiteruleConverter.INSTANCE;

  @Override
  public void saveVersion(RuleVersionDTO saveDTO) {
    RuleVersionHistory entity = converter.postDtoToEntity(saveDTO);
    ruleVersionHistoryMapper.insert(entity);
  }

  @Override
  public List<RuleVersionVO> listVersions(String ruleCode) {
    LambdaQueryWrapper<RuleVersionHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RuleVersionHistory::getRuleCode, ruleCode)
           .orderByDesc(RuleVersionHistory::getVersion);
    List<RuleVersionHistory> entities = ruleVersionHistoryMapper.selectList(wrapper);
    return converter.ruleVersionListToVO(entities);
  }

  @Override
  public PageResponse<List<RuleVersionVO>> pageVersions(String ruleCode, int pageNum, int pageSize) {
    Page<RuleVersionHistory> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<RuleVersionHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RuleVersionHistory::getRuleCode, ruleCode)
           .orderByDesc(RuleVersionHistory::getVersion);
    IPage<RuleVersionHistory> entityPage = ruleVersionHistoryMapper.selectPage(page, wrapper);
    return PageResponse.success(
        (long) pageNum, (long) pageSize, entityPage.getTotal(),
        converter.ruleVersionListToVO(entityPage.getRecords()));
  }

  @Override
  public Optional<RuleDefinitionVO> rollback(String ruleCode, int version, String operator) {
    // 1. 查询目标版本
    LambdaQueryWrapper<RuleVersionHistory> versionWrapper = new LambdaQueryWrapper<>();
    versionWrapper.eq(RuleVersionHistory::getRuleCode, ruleCode)
                 .eq(RuleVersionHistory::getVersion, version);
    RuleVersionHistory targetVersion = ruleVersionHistoryMapper.selectOne(versionWrapper);
    if (targetVersion == null) {
      log.warn("[LiteRule] 回滚目标版本不存在: ruleCode={}, version={}", ruleCode, version);
      return Optional.empty();
    }

    // 2. 查询当前规则定义
    RuleDefinitionDTO currentRule = ruleDefinitionMapper.selectByCode(ruleCode);
    if (currentRule == null) {
      log.warn("[LiteRule] 回滚时规则定义不存在: ruleCode={}", ruleCode);
      return Optional.empty();
    }

    // 3. 将当前版本备份到版本历史表（保护性快照，防止回滚后无法恢复）
    String currentDefinitionJson = YdszJson.toJson(apiFromDo(currentRule));
    Integer nextVersion = calculateNextVersionNumber(ruleCode);
    RuleVersionHistory backupVersion = RuleVersionHistory.builder()
        .ruleCode(ruleCode)
        .version(nextVersion)
        .definitionJson(currentDefinitionJson)
        .changeDesc(String.format("[回滚前快照] 回滚前自动备份, 操作人=%s", operator))
        .operator(operator)
        .build();
    ruleVersionHistoryMapper.insert(backupVersion);
    log.info("[LiteRule] 回滚前快照已保存: ruleCode={}, backupVersion={}", ruleCode, nextVersion);

    // 4. 从目标版本反序列化规则定义并恢复到主表
    com.njydsz.literule.domain.dto.RuleDefinitionDTO targetDefinition; // FQN-OK: name conflict with infra entity RuleDefinitionDTO
    try {
      targetDefinition = YdszJson.fromJson(
          targetVersion.getDefinitionJson(), RuleDefinition.class);
    } catch (Exception e) {
      log.error("[LiteRule] 反序列化目标版本失败: ruleCode={}, version={}, error={}",
          ruleCode, version, e.getMessage(), e);
      throw new IllegalStateException("回滚失败：目标版本 JSON 解析异常", e);
    }

    RuleDefinitionDTO updateEntity = doFromApi(targetDefinition);
    updateEntity.setId(currentRule.getId());
    updateEntity.setRuleCode(ruleCode);
    // 新版本号 = 当前最大版本号 + 1，保持递增
    updateEntity.setVersion(nextVersion + 1);
    // 回滚后默认进入 DRAFT 状态，需要重新审批
    updateEntity.setStatus("DRAFT");
    updateEntity.setReviewedBy(null);
    updateEntity.setReviewedAt(null);
    updateEntity.setReviewComment(null);

    int updated = ruleDefinitionMapper.updateById(updateEntity);
    if (updated <= 0) {
      throw new IllegalStateException("回滚失败：规则定义更新失败，可能存在并发冲突");
    }

    // 5. 保存回滚操作版本记录
    RuleVersionDTO rollbackRecord = new RuleVersionDTO();
    rollbackRecord.setRuleCode(ruleCode);
    rollbackRecord.setRuleName(updateEntity.getRuleName());
    rollbackRecord.setVersion(updateEntity.getVersion());
    rollbackRecord.setDefinitionJson(YdszJson.toJson(targetDefinition));
    rollbackRecord.setChangeDesc(String.format("[回滚] 从版本 %d 回滚到版本 %d, 操作人=%s",
        currentRule.getVersion(), version, operator));
    rollbackRecord.setOperator(operator);
    saveVersion(rollbackRecord);

    log.info("[LiteRule] 规则回滚完成: ruleCode={}, targetVersion={}, newVersion={}, operator={}",
        ruleCode, version, updateEntity.getVersion(), operator);

    // 6. 返回回滚后的规则定义 VO
    RuleDefinitionDTO refreshedRule = ruleDefinitionMapper.selectByCode(ruleCode);
    return Optional.ofNullable(converter.entityToVO(refreshedRule));
  }

  /**
   * 计算下一个版本号
   *
   * <p>取当前规则的最大版本号 + 1
   *
   * @param ruleCode 规则编码
   * @return 下一个可用版本号
   */
  private Integer calculateNextVersionNumber(String ruleCode) {
    LambdaQueryWrapper<RuleVersionHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RuleVersionHistory::getRuleCode, ruleCode)
           .orderByDesc(RuleVersionHistory::getVersion)
           .last("LIMIT 1");
    RuleVersionHistory latest = ruleVersionHistoryMapper.selectOne(wrapper);
    return latest != null ? latest.getVersion() + 1 : 1;
  }

  /**
   * RuleDefinitionDTO → RuleDefinitionDTO (api) 转换
   *
   * @param rule 规则定义
   * @return RuleDefinitionDTO api 定义
   */
  private com.njydsz.literule.domain.dto.RuleDefinitionDTO apiFromDo(RuleDefinitionDTO rule) { // FQN-OK: name conflict with infra entity RuleDefinitionDTO
    com.njydsz.literule.domain.dto.RuleDefinitionDTO def = new com.njydsz.literule.domain.dto.RuleDefinitionDTO(); // FQN-OK: name conflict with infra entity RuleDefinitionDTO
    def.setCode(rule.getRuleCode());
    def.setName(rule.getRuleName());
    def.setCategory(rule.getCategory());
    def.setCategoryPath(rule.getCategoryPath());
    def.setOwner(rule.getOwner());
    def.setDescription(rule.getDescription());
    def.setConditionExpression(rule.getConditionExpression());
    def.setSeverityExpression(rule.getSeverityExpression());
    def.setDefaultSeverity(
        rule.getDefaultSeverity() != null
            ? RuleSeverity.fromCode(rule.getDefaultSeverity())
            : null);
    def.setTitleTemplate(rule.getTitleTemplate());
    def.setDescriptionTemplate(rule.getDescriptionTemplate());
    def.setPriority(rule.getPriority());
    def.setEnabled(rule.getEnabled() != null && rule.getEnabled());
    def.setScope(rule.getScope());
    def.setMutexGroup(rule.getMutexGroup());
    def.setVersion(rule.getVersion() != null ? rule.getVersion() : 1);
    def.setStatus(rule.getStatus());
    def.setEffectiveFrom(rule.getEffectiveFrom());
    def.setEffectiveTo(rule.getEffectiveTo());
    def.setReviewedBy(rule.getReviewedBy());
    def.setReviewedAt(rule.getReviewedAt());
    def.setReviewComment(rule.getReviewComment());
    def.setCanaryRatio(rule.getCanaryRatio() != null ? rule.getCanaryRatio() : 0.0);
    def.setCanaryConditionExpression(rule.getCanaryConditionExpression());
    def.setCanarySeverityExpression(rule.getCanarySeverityExpression());
    return def;
  }

  /**
   * RuleDefinitionDTO (api) → RuleDefinitionDTO 转换
   *
   * @param def api 规则定义
   * @return RuleDefinitionDTO
   */
  private RuleDefinitionDTO doFromApi(com.njydsz.literule.domain.dto.RuleDefinitionDTO def) { // FQN-OK: name conflict with infra entity RuleDefinitionDTO
    RuleDefinitionDTO rule = new RuleDefinitionDTO();
    rule.setRuleCode(def.getCode());
    rule.setRuleName(def.getName());
    rule.setCategory(def.getCategory());
    rule.setCategoryPath(def.getCategoryPath());
    rule.setOwner(def.getOwner());
    rule.setDescription(def.getDescription());
    rule.setConditionExpression(def.getConditionExpression());
    rule.setSeverityExpression(def.getSeverityExpression());
    rule.setDefaultSeverity(
        def.getDefaultSeverity() != null ? def.getDefaultSeverity().getCode() : null);
    rule.setTitleTemplate(def.getTitleTemplate());
    rule.setDescriptionTemplate(def.getDescriptionTemplate());
    rule.setPriority(def.getPriority());
    rule.setEnabled(def.isEnabled());
    rule.setScope(def.getScope());
    rule.setMutexGroup(def.getMutexGroup());
    rule.setStatus(def.getStatus());
    rule.setEffectiveFrom(def.getEffectiveFrom());
    rule.setEffectiveTo(def.getEffectiveTo());
    return rule;
  }
}




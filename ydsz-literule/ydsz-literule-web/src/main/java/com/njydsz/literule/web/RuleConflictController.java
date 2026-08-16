package com.njydsz.literule.web;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.literule.domain.vo.RuleConflictInfoVO;
import com.njydsz.literule.server.spi.RuleConflictDetectorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则冲突检测 Controller
 *
 * <p>业务背景：随着规则数量增长，多条规则之间可能出现条件重叠、严重度矛盾、 优先级倒挂等冲突，影响规则引擎的判定准确性。冲突检测器通过 SPI 由 project
 * 模块提供实现，对当前生效规则集做静态分析并返回冲突对列表。
 *
 * <p>核心能力：检测当前规则集中的冲突规则对。
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/rule-engine/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则冲突检测", description = "规则冲突检测")
public class RuleConflictController {

  /** 规则冲突检测器（SPI，由 project 模块提供实现） */
  private final RuleConflictDetectorProvider ruleConflictDetectorProvider;

  /**
   * 检测规则冲突
   *
   * @return 冲突规则对列表
   */
  @GetMapping("/conflicts")
  public BaseResponse<List<RuleConflictInfoVO>> detectConflicts() {
    return BaseResponse.success(
        ruleConflictDetectorProvider.detectConflicts().stream()
            .map(LiteruleWebConverter.INSTANT::entityToVO)
            .toList());
  }
}

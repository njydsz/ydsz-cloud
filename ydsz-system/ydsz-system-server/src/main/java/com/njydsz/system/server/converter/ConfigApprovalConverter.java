package com.njydsz.system.server.converter;

import org.springframework.stereotype.Component;

import com.njydsz.system.domain.approval.ConfigApprovalVO;

/**
 * 配置变更审批单 VO 增强器。
 *
 * <p>为 MapStruct 生成的 VO 填充计算字段（title / submitterName / currentApproverName）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Component
public class ConfigApprovalConverter {

  /**
   * 为 VO 填充计算字段。
   *
   * <p>MapStruct 仅做字段映射，title / submitterName / currentApproverName 需手工填充。
   *
   * @param vo 审批单 VO（已由 MapStruct 从 Entity 映射基础字段）
   * @return 填充完成后的 VO（同一实例）
   */
  public ConfigApprovalVO toVO(ConfigApprovalVO vo) {
    if (vo == null) {
      return null;
    }
    vo.setTitle(buildTitle(vo));
    // submitterName / currentApproverName 需由 service 层或前端额外查询用户表填充
    return vo;
  }

  /**
   * 构建展示标题。
   *
   * @param vo 审批单 VO
   * @return 标题字符串
   */
  private String buildTitle(ConfigApprovalVO vo) {
    String changeOp;
    switch (vo.getChangeType()) {
      case "CREATE":
        changeOp = "创建";
        break;
      case "UPDATE":
        changeOp = "修改";
        break;
      case "DELETE":
        changeOp = "删除";
        break;
      default:
        changeOp = "变更";
    }
    return changeOp + vo.getResourceType() + ":" + vo.getResourceKey();
  }
}

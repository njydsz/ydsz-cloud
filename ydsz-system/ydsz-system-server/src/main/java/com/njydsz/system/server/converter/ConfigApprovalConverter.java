package com.njydsz.system.server.converter;

import com.njydsz.system.domain.approval.ConfigApproval;
import com.njydsz.system.domain.approval.ConfigApprovalVO;
import org.springframework.stereotype.Component;

/**
 * 配置变更审批单 Converter。
 *
 * <p>手工映射（非 MapStruct），负责 ConfigApproval Entity → VO 的转换。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Component
public class ConfigApprovalConverter {

  /**
   * Entity → VO 转换。
   *
   * @param entity 审批单实体
   * @return 展示视图对象
   */
  public ConfigApprovalVO toVO(ConfigApproval entity) {
    if (entity == null) {
      return null;
    }
    ConfigApprovalVO vo = new ConfigApprovalVO();
    vo.setId(entity.getId());
    vo.setResourceType(entity.getResourceType());
    vo.setResourceKey(entity.getResourceKey());
    vo.setResourceGroup(entity.getResourceGroup());
    vo.setChangeType(entity.getChangeType());
    vo.setBeforeJson(entity.getBeforeJson());
    vo.setAfterJson(entity.getAfterJson());
    vo.setStatus(entity.getStatus());
    vo.setSubmitterId(entity.getSubmitterId());
    vo.setSubmittedAt(entity.getSubmittedAt());
    vo.setReason(entity.getReason());
    vo.setRejectionReason(entity.getRejectionReason());
    vo.setClosedAt(entity.getClosedAt());
    vo.setTitle(buildTitle(entity));
    return vo;
  }

  /**
   * 构建展示标题。
   *
   * @param entity 审批单实体
   * @return 标题字符串
   */
  private String buildTitle(ConfigApproval entity) {
    String changeOp;
    switch (entity.getChangeType()) {
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
    return changeOp + entity.getResourceType() + ":" + entity.getResourceKey();
  }
}

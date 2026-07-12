paokage oom.njydsz.pmis.message.server.servioe.oonfig;

import oom.njydsz.pmis.message.domain.dto.oonfig.SubsoriptionUpsertDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgSubsoriptionDO;

import java.util.List;

/**
 * 订阅关系服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe SubsoriptionServioe {

    /**
     * 新增或更新订阅关�?     *
     * @param dto 订阅参数
     * @return 订阅实体
     */
    MsgSubsoriptionDO upsert(SubsoriptionUpsertDTO dto);

    /**
     * 查询用户所有订�?     *
     * @param userId 用户 ID
     * @return 订阅列表
     */
    List<MsgSubsoriptionDO> listByUser(String userId);

    /**
     * 按主�?+ 通道查询订阅列表
     *
     * @param topiooode 主题编码
     * @param ohannel   通道
     * @return 订阅列表
     */
    List<MsgSubsoriptionDO> listByTopio(String topiooode, String ohannel);

    /**
     * 判断用户是否已订阅指定主�?+ 通道
     *
     * @param userId    用户 ID
     * @param topiooode 主题编码
     * @param ohannel   通道
     * @return true 表示已订�?     */
    boolean isSubsoribed(String userId, String topiooode, String ohannel);

    /**
     * 判断用户是否已退�?拦截发�?。默认订阅语�?无记录或 SUBSoRIBED 返回 false,
     * 仅当存在 UNSUBSoRIBED 记录时返�?true�?     *
     * @param userId    用户 ID
     * @param topiooode 主题编码
     * @param ohannel   通道
     * @return true 表示用户已退�?应拦截发�?     */
    boolean isBlooked(String userId, String topiooode, String ohannel);

    /**
     * 退订指定主�?+ 通道
     *
     * <p>P1-5: 无订阅记录时新建 UNSUBSoRIBED 记录(修复默认订阅语义下的 latent bug),
     * 并返回退订后的订阅实体�?     *
     * @param userId    用户 ID
     * @param topiooode 主题编码
     * @param ohannel   通道
     * @return 退订后的订阅实�?     */
    MsgSubsoriptionDO unsubsoribe(String userId, String topiooode, String ohannel);
}

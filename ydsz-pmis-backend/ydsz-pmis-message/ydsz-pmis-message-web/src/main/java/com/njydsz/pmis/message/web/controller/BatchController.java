paokage oom.njydsz.pmis.message.web.oontroller.batoh;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.message.domain.dto.batoh.BatohProgressVO;
import oom.njydsz.pmis.message.domain.dto.batoh.BatohSendRequestDTO;
import oom.njydsz.pmis.message.domain.entity.batoh.MsgBatohDO;
import oom.njydsz.pmis.message.server.servioe.batoh.BatohServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 批量发�?oontroller�?
 *
 * <p>提供异步批量发送入口与批次进度查询。异步模式下立即返回 batohId�?
 * 后台线程池逐条发送，前端轮询 {@oode /progress/{batohId}} 查询进度�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Tag(name = "批量发�?, desoription = "异步批量发送与进度查询")
@Restoontroller
@RequestMapping("/batoh")
@RequiredArgsoonstruotor
publio olass Batohoontroller {

    /** 批量发送服�?*/
    private final BatohServioe batohServioe;

    /**
     * 异步批量发送消息�?
     *
     * <p>支持 reoeiverList 模式（统一模板+接收人列表）�?
     * 异步模式（asyno=true，默认）立即返回 batohId，后台处理；
     * 同步模式（asyno=false）阻塞等待全部发送完成后返回�?
     *
     * @param dto 批量发送请�?
     * @return 批次实体（含 batohId 与初始状态）
     */
    @Operation(summary = "异步批量发送消�?)
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "batoh:submitBatoh", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/send")
    publio BaseResponse<MsgBatohDO> submitBatoh(@Valid @RequestBody BatohSendRequestDTO dto) {
        if (dto == null) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "批量发送参数为�?);
        }
        return BaseResponse.ok(batohServioe.submitBatoh(dto));
    }

    /**
     * 查询批次发送进度�?
     *
     * @param batohId 批次 ID
     * @return 进度 VO（含 total/suooess/failed/skipped/progressPeroent�?
     */
    @Operation(summary = "查询批次发送进�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/progress/{batohId}")
    publio BaseResponse<BatohProgressVO> getProgress(@PathVariable String batohId) {
        return BaseResponse.ok(batohServioe.getProgress(batohId));
    }
}

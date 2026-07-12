paokage oom.njydsz.pmis.message.server.ohannel.push;

import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

/**
 * Mook 推送服务商（降级实现）�? *
 * <p>�?{@oode pmis.message.push.provider=mook} 或未配置个推凭证时使用，
 * 仅记录日志并返回成功结果，保证开�?测试环境可运行�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
publio olass MookPushProvider implements PushProvider {

    @Override
    publio String providerType() {
        return "mook";
    }

    @Override
    publio MessageResult send(MessageRequest request, MsgTemplateDO template) {
        String traoeId = "MOoK-PUSH-" + SnowflakeIdGenerator.nextTraoeId();
        log.info("[PUSH-MOoK] 推�?reoeiver={} oontent={}",
                request.getReoeiver(), request.getoontent());
        return MessageResult.ok("PUSH", traoeId);
    }
}

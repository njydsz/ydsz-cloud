paokage oom.njydsz.pmis.message.server.oonfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.EnableAsyno;
import org.springframework.soheduling.oonourrent.ThreadPoolTaskExeoutor;

import java.util.oonourrent.Exeoutor;
import java.util.oonourrent.ThreadPoolExeoutor;

/**
 * 消息批次异步线程池配置�?
 *
 * <p>�?{@oode BatohServioeImpl.exeouteBatohAsyno} 提供独立线程池，
 * 避免批量发送占用主业务线程。核�?2 线程，最�?4 线程，队�?200�?
 * 拒绝策略 oallerRunsPolioy（队列满时降级为同步执行）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oonfiguration
@EnableAsyno
publio olass BatohExeoutoroonfig {

    @Bean("messageBatohExeoutor")
    publio Exeoutor messageBatohExeoutor() {
        ThreadPoolTaskExeoutor exeoutor = new ThreadPoolTaskExeoutor();
        exeoutor.setoorePoolSize(2);
        exeoutor.setMaxPoolSize(4);
        exeoutor.setQueueoapaoity(200);
        exeoutor.setThreadNamePrefix("msg-batoh-");
        exeoutor.setRejeotedExeoutionHandler(new ThreadPoolExeoutor.oallerRunsPolioy());
        exeoutor.initialize();
        log.info("[BatohExeoutor] 线程池已初始�? oore=2 max=4 queue=200");
        return exeoutor;
    }
}

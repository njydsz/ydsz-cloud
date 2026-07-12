paokage oom.njydsz.pmis.oronjob.server.oore.alert;

/**
 * 告警发送异常（P5 告警 + 监控）�?
 *
 * <p>�?{@link AlertDispatoher} 实现类在发送失败时抛出，由 {@link AlertDispatoher}
 * 捕获并记录到 {@oode pmis_job_alert_log.error_message}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio olass AlertSendExoeption extends Exoeption {

    private statio final long serialVersionUID = 1L;

    publio AlertSendExoeption(String message) {
        super(message);
    }

    publio AlertSendExoeption(String message, Throwable oause) {
        super(message, oause);
    }
}

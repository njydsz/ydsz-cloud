package com.njydsz.common.permission;

/**
 * 全局权限码常量池。
 *
 * <p>集中管理各业务模块的接口权限码，供 {@code @AuthApiPermission} 注解引用，
 * 避免各模块重复定义或硬编码字符串。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class PermissionCodes {

    private PermissionCodes() {
        // utility class
    }

    /** CRONJOB_ALERT_CREATE */
    public static final String CRONJOB_ALERT_CREATE = "cronjob:alert:create";

    /** CRONJOB_ALERT_DELETE */
    public static final String CRONJOB_ALERT_DELETE = "cronjob:alert:delete";

    /** CRONJOB_ALERT_UPDATE */
    public static final String CRONJOB_ALERT_UPDATE = "cronjob:alert:update";

    /** CRONJOB_ALERT_VIEW */
    public static final String CRONJOB_ALERT_VIEW = "cronjob:alert:view";

    /** CRONJOB_DAG_CREATE */
    public static final String CRONJOB_DAG_CREATE = "cronjob:dag:create";

    /** CRONJOB_DAG_DELETE */
    public static final String CRONJOB_DAG_DELETE = "cronjob:dag:delete";

    /** CRONJOB_DAG_TRIGGER */
    public static final String CRONJOB_DAG_TRIGGER = "cronjob:dag:trigger";

    /** CRONJOB_DAG_UPDATE */
    public static final String CRONJOB_DAG_UPDATE = "cronjob:dag:update";

    /** CRONJOB_DAG_VIEW */
    public static final String CRONJOB_DAG_VIEW = "cronjob:dag:view";

    /** CRONJOB_GLUE_MANAGE */
    public static final String CRONJOB_GLUE_MANAGE = "cronjob:glue:manage";

    /** CRONJOB_GLUE_TEST */
    public static final String CRONJOB_GLUE_TEST = "cronjob:glue:test";

    /** CRONJOB_GLUE_VIEW */
    public static final String CRONJOB_GLUE_VIEW = "cronjob:glue:view";

    /** CRONJOB_JOB_CREATE */
    public static final String CRONJOB_JOB_CREATE = "cronjob:job:create";

    /** CRONJOB_JOB_DELETE */
    public static final String CRONJOB_JOB_DELETE = "cronjob:job:delete";

    /** CRONJOB_JOB_PAUSE */
    public static final String CRONJOB_JOB_PAUSE = "cronjob:job:pause";

    /** CRONJOB_JOB_RELOAD */
    public static final String CRONJOB_JOB_RELOAD = "cronjob:job:reload";

    /** CRONJOB_JOB_TRIGGER */
    public static final String CRONJOB_JOB_TRIGGER = "cronjob:job:trigger";

    /** CRONJOB_JOB_UPDATE */
    public static final String CRONJOB_JOB_UPDATE = "cronjob:job:update";

    /** CRONJOB_JOB_VIEW */
    public static final String CRONJOB_JOB_VIEW = "cronjob:job:view";

    /** CRONJOB_STATS_VIEW */
    public static final String CRONJOB_STATS_VIEW = "cronjob:stats:view";

    /** MESSAGE_AGGREGATE_LIST */
    public static final String MESSAGE_AGGREGATE_LIST = "message:aggregate:list";

    /** MESSAGE_AGGREGATE_REFRESH */
    public static final String MESSAGE_AGGREGATE_REFRESH = "message:aggregate:refresh";

    /** MESSAGE_CANARY_REPORT */
    public static final String MESSAGE_CANARY_REPORT = "message:canary:report";

    /** MESSAGE_CANARY_UPDATE */
    public static final String MESSAGE_CANARY_UPDATE = "message:canary:update";

    /** MESSAGE_CANARY_VIEW */
    public static final String MESSAGE_CANARY_VIEW = "message:canary:view";

    /** MESSAGE_DEAD_LETTER_RESEND */
    public static final String MESSAGE_DEAD_LETTER_RESEND = "message:dead:letter:resend";

    /** MESSAGE_DEAD_LETTER_VIEW */
    public static final String MESSAGE_DEAD_LETTER_VIEW = "message:dead:letter:view";

    /** MESSAGE_LOG_VIEW */
    public static final String MESSAGE_LOG_VIEW = "message:log:view";

    /** MESSAGE_PREFERENCE_DELETE */
    public static final String MESSAGE_PREFERENCE_DELETE = "message:preference:delete";

    /** MESSAGE_PREFERENCE_UPDATE */
    public static final String MESSAGE_PREFERENCE_UPDATE = "message:preference:update";

    /** MESSAGE_PREFERENCE_VIEW */
    public static final String MESSAGE_PREFERENCE_VIEW = "message:preference:view";

    /** MESSAGE_RECALL_ACT */
    public static final String MESSAGE_RECALL_ACT = "message:recall:act";

    /** MESSAGE_RECEIPT_CALLBACK */
    public static final String MESSAGE_RECEIPT_CALLBACK = "message:receipt:callback";

    /** MESSAGE_RECEIPT_VIEW */
    public static final String MESSAGE_RECEIPT_VIEW = "message:receipt:view";

    /** MESSAGE_ROUTE_RULE_CREATE */
    public static final String MESSAGE_ROUTE_RULE_CREATE = "message:route:rule:create";

    /** MESSAGE_ROUTE_RULE_DELETE */
    public static final String MESSAGE_ROUTE_RULE_DELETE = "message:route:rule:delete";

    /** MESSAGE_ROUTE_RULE_LIST */
    public static final String MESSAGE_ROUTE_RULE_LIST = "message:route:rule:list";

    /** MESSAGE_ROUTE_RULE_UPDATE */
    public static final String MESSAGE_ROUTE_RULE_UPDATE = "message:route:rule:update";

    /** MESSAGE_ROUTE_RULE_VIEW */
    public static final String MESSAGE_ROUTE_RULE_VIEW = "message:route:rule:view";

    /** MESSAGE_SUBSCRIPTION_DELETE */
    public static final String MESSAGE_SUBSCRIPTION_DELETE = "message:subscription:delete";

    /** MESSAGE_SUBSCRIPTION_LIST */
    public static final String MESSAGE_SUBSCRIPTION_LIST = "message:subscription:list";

    /** MESSAGE_SUBSCRIPTION_UPDATE */
    public static final String MESSAGE_SUBSCRIPTION_UPDATE = "message:subscription:update";

    /** MESSAGE_TEMPLATE_APPROVE */
    public static final String MESSAGE_TEMPLATE_APPROVE = "message:template:approve";

    /** MESSAGE_TEMPLATE_CREATE */
    public static final String MESSAGE_TEMPLATE_CREATE = "message:template:create";

    /** MESSAGE_TEMPLATE_DELETE */
    public static final String MESSAGE_TEMPLATE_DELETE = "message:template:delete";

    /** MESSAGE_TEMPLATE_LIST */
    public static final String MESSAGE_TEMPLATE_LIST = "message:template:list";

    /** MESSAGE_TEMPLATE_UPDATE */
    public static final String MESSAGE_TEMPLATE_UPDATE = "message:template:update";

    /** MESSAGE_TEMPLATE_VIEW */
    public static final String MESSAGE_TEMPLATE_VIEW = "message:template:view";

    /** MESSAGE_UNSUBSCRIBE_ACT */
    public static final String MESSAGE_UNSUBSCRIBE_ACT = "message:unsubscribe:act";

    /** MESSAGE_UNSUBSCRIBE_VIEW */
    public static final String MESSAGE_UNSUBSCRIBE_VIEW = "message:unsubscribe:view";

    /** NEXTWIKI_ANALYSIS */
    public static final String NEXTWIKI_ANALYSIS = "nextwiki:analysis";

    /** NEXTWIKI_BATCH_IMPORT */
    public static final String NEXTWIKI_BATCH_IMPORT = "nextwiki:batch:import";

    /** NEXTWIKI_DOWNLOAD */
    public static final String NEXTWIKI_DOWNLOAD = "nextwiki:download";

    /** NEXTWIKI_FILE_COPY */
    public static final String NEXTWIKI_FILE_COPY = "nextwiki:file:copy";

    /** NEXTWIKI_FILE_DELETE */
    public static final String NEXTWIKI_FILE_DELETE = "nextwiki:file:delete";

    /** NEXTWIKI_FILE_LIST */
    public static final String NEXTWIKI_FILE_LIST = "nextwiki:file:list";

    /** NEXTWIKI_FILE_MOVE */
    public static final String NEXTWIKI_FILE_MOVE = "nextwiki:file:move";

    /** NEXTWIKI_FILE_RENAME */
    public static final String NEXTWIKI_FILE_RENAME = "nextwiki:file:rename";

    /** NEXTWIKI_FILE_STAR */
    public static final String NEXTWIKI_FILE_STAR = "nextwiki:file:star";

    /** NEXTWIKI_FILE_UPLOAD */
    public static final String NEXTWIKI_FILE_UPLOAD = "nextwiki:file:upload";

    /** NEXTWIKI_FILE_VERSION_ROLLBACK */
    public static final String NEXTWIKI_FILE_VERSION_ROLLBACK = "nextwiki:file:version:rollback";

    /** NEXTWIKI_FILE_VERSION_VIEW */
    public static final String NEXTWIKI_FILE_VERSION_VIEW = "nextwiki:file:version:view";

    /** NEXTWIKI_FILE_VIEW */
    public static final String NEXTWIKI_FILE_VIEW = "nextwiki:file:view";

    /** NEXTWIKI_FOLDER_CREATE */
    public static final String NEXTWIKI_FOLDER_CREATE = "nextwiki:folder:create";

    /** NEXTWIKI_PREVIEW_GENERATE */
    public static final String NEXTWIKI_PREVIEW_GENERATE = "nextwiki:preview:generate";

    /** NEXTWIKI_PREVIEW_VIEW */
    public static final String NEXTWIKI_PREVIEW_VIEW = "nextwiki:preview:view";

    /** NEXTWIKI_QUOTA_SET */
    public static final String NEXTWIKI_QUOTA_SET = "nextwiki:quota:set";

    /** NEXTWIKI_QUOTA_VIEW */
    public static final String NEXTWIKI_QUOTA_VIEW = "nextwiki:quota:view";

    /** NEXTWIKI_SEARCH */
    public static final String NEXTWIKI_SEARCH = "nextwiki:search";

    /** NEXTWIKI_SEARCH_REBUILD */
    public static final String NEXTWIKI_SEARCH_REBUILD = "nextwiki:search:rebuild";

    /** NEXTWIKI_SHARE_CREATE */
    public static final String NEXTWIKI_SHARE_CREATE = "nextwiki:share:create";

    /** NEXTWIKI_SHARE_LIST */
    public static final String NEXTWIKI_SHARE_LIST = "nextwiki:share:list";

    /** NEXTWIKI_SHARE_REVOKE */
    public static final String NEXTWIKI_SHARE_REVOKE = "nextwiki:share:revoke";

    /** NEXTWIKI_SHARE_VERIFY */
    public static final String NEXTWIKI_SHARE_VERIFY = "nextwiki:share:verify";

    /** NEXTWIKI_TAG_BIND */
    public static final String NEXTWIKI_TAG_BIND = "nextwiki:tag:bind";

    /** NEXTWIKI_TAG_CREATE */
    public static final String NEXTWIKI_TAG_CREATE = "nextwiki:tag:create";

    /** NEXTWIKI_TAG_LIST */
    public static final String NEXTWIKI_TAG_LIST = "nextwiki:tag:list";

    /** NEXTWIKI_TRASH_EMPTY */
    public static final String NEXTWIKI_TRASH_EMPTY = "nextwiki:trash:empty";

    /** NEXTWIKI_TRASH_LIST */
    public static final String NEXTWIKI_TRASH_LIST = "nextwiki:trash:list";

    /** NEXTWIKI_TRASH_PURGE */
    public static final String NEXTWIKI_TRASH_PURGE = "nextwiki:trash:purge";

    /** NEXTWIKI_TRASH_RESTORE */
    public static final String NEXTWIKI_TRASH_RESTORE = "nextwiki:trash:restore";

    /** NOTIF_BROADCAST */
    public static final String NOTIF_BROADCAST = "notif:broadcast";

    /** NOTIF_MESSAGE_DELETE */
    public static final String NOTIF_MESSAGE_DELETE = "notif:message:delete";

    /** NOTIF_MESSAGE_LIST */
    public static final String NOTIF_MESSAGE_LIST = "notif:message:list";

    /** NOTIF_MESSAGE_RECALL */
    public static final String NOTIF_MESSAGE_RECALL = "notif:message:recall";

    /** NOTIF_MESSAGE_SEND */
    public static final String NOTIF_MESSAGE_SEND = "notif:message:send";

    /** NOTIF_MESSAGE_VIEW */
    public static final String NOTIF_MESSAGE_VIEW = "notif:message:view";

    /** NOTIF_PUSH */
    public static final String NOTIF_PUSH = "notif:push";

    /** NOTIF_TEMPLATE_AUDIT */
    public static final String NOTIF_TEMPLATE_AUDIT = "notif:template:audit";

    /** NOTIF_TEMPLATE_VIEW */
    public static final String NOTIF_TEMPLATE_VIEW = "notif:template:view";

    /** WORKFLOW_CANARY_MANAGE */
    public static final String WORKFLOW_CANARY_MANAGE = "workflow:canary:manage";

    /** WORKFLOW_CC_VIEW */
    public static final String WORKFLOW_CC_VIEW = "workflow:cc:view";

    /** WORKFLOW_DEFINITION_DEPLOY */
    public static final String WORKFLOW_DEFINITION_DEPLOY = "workflow:definition:deploy";

    /** WORKFLOW_DEFINITION_DESIGN */
    public static final String WORKFLOW_DEFINITION_DESIGN = "workflow:definition:design";

    /** WORKFLOW_DEFINITION_IMPORT */
    public static final String WORKFLOW_DEFINITION_IMPORT = "workflow:definition:import";

    /** WORKFLOW_DEFINITION_PUBLISH */
    public static final String WORKFLOW_DEFINITION_PUBLISH = "workflow:definition:publish";

    /** WORKFLOW_DELEGATE_MANAGE */
    public static final String WORKFLOW_DELEGATE_MANAGE = "workflow:delegate:manage";

    /** WORKFLOW_INSTANCE_CONTROL */
    public static final String WORKFLOW_INSTANCE_CONTROL = "workflow:instance:control";

    /** WORKFLOW_INSTANCE_RESUBMIT */
    public static final String WORKFLOW_INSTANCE_RESUBMIT = "workflow:instance:resubmit";

    /** WORKFLOW_INSTANCE_ROLLBACK */
    public static final String WORKFLOW_INSTANCE_ROLLBACK = "workflow:instance:rollback";

    /** WORKFLOW_INSTANCE_START */
    public static final String WORKFLOW_INSTANCE_START = "workflow:instance:start";

    /** WORKFLOW_INSTANCE_VIEW */
    public static final String WORKFLOW_INSTANCE_VIEW = "workflow:instance:view";

    /** WORKFLOW_MONITOR */
    public static final String WORKFLOW_MONITOR = "workflow:monitor";

    /** WORKFLOW_MONITOR_VIEW */
    public static final String WORKFLOW_MONITOR_VIEW = "workflow:monitor:view";

    /** WORKFLOW_SLA_CONFIG */
    public static final String WORKFLOW_SLA_CONFIG = "workflow:sla:config";

    /** WORKFLOW_TASK_FREE_JUMP */
    public static final String WORKFLOW_TASK_FREE_JUMP = "workflow:task:free:jump";

    /** WORKFLOW_TASK_OPERATE */
    public static final String WORKFLOW_TASK_OPERATE = "workflow:task:operate";

    /** WORKFLOW_TASK_VIEW */
    public static final String WORKFLOW_TASK_VIEW = "workflow:task:view";

    /** WORKFLOW_TEMPLATE_IMPORT */
    public static final String WORKFLOW_TEMPLATE_IMPORT = "workflow:template:import";

    /** PROJECT_SEARCH */
    public static final String PROJECT_SEARCH = "project:search";

    /** PROJECT_SEARCH_REBUILD */
    public static final String PROJECT_SEARCH_REBUILD = "project:search:rebuild";

    /** USERINFO_SEARCH */
    public static final String USERINFO_SEARCH = "userinfo:search";

    /** SYSTEM_SEARCH */
    public static final String SYSTEM_SEARCH = "system:search";

}

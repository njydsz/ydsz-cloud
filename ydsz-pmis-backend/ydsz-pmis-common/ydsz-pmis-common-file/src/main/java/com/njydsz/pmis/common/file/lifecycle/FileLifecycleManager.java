package com.njydsz.pmis.common.file.lifecycle;

import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IStorageFactory;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件生命周期管理器
 *
 * <p>支持基于规则的自动过期清理，按文件路径前缀配置不同的保留策略，
 * 定时扫描并清理过期文件。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * remi:
 *   file:
 *     lifecycle:
 *       enabled: true
 *       cron: "0 0 2 * * ?"  # 每天凌晨2点执行
 *       rules:
 *         - prefix: "temp/"
 *           maxAgeDays: 7
 *           action: delete
 *         - prefix: "logs/"
 *           maxAgeDays: 30
 *           action: delete
 *         - prefix: "archive/"
 *           maxAgeDays: 365
 *           action: delete
 *       dry-run: false
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "remi.file.lifecycle", name = "enabled", havingValue = "true")
public class FileLifecycleManager {

	private final FileLifecycleProperties lifecycleProperties;
	private final IStorageFactory storageFactory;
	private final IFileStorage fileStorage;

	/**
	 * 定时执行文件清理任务
	 *
	 * <p>根据配置的 cron 表达式定时触发，遍历所有规则并执行过期文件清理。
	 */
	@Scheduled(cron = "${remi.file.lifecycle.cron:0 0 2 * * ?}")
	public void executeCleanup() {
		if (!lifecycleProperties.isEnabled()) {
			log.debug("文件生命周期清理未启用，跳过执行");
			return;
		}

		List<FileLifecycleProperties.LifecycleRule> rules = lifecycleProperties.getRules();
		if (rules == null || rules.isEmpty()) {
			log.debug("文件生命周期清理规则为空，跳过执行");
			return;
		}

		IFileStorage storage = resolveStorage();
		if (storage == null) {
			log.error("文件生命周期清理失败：无法获取文件存储实例");
			return;
		}

		String bucketName = lifecycleProperties.getBucket();
		boolean dryRun = lifecycleProperties.isDryRun();

		log.info("开始执行文件生命周期清理, ruleCount={}, dryRun={}", rules.size(), dryRun);

		for (FileLifecycleProperties.LifecycleRule rule : rules) {
			try {
				processRule(storage, bucketName, rule, dryRun);
			} catch (Exception e) {
				log.error("文件生命周期清理规则执行失败, prefix={}: {}",
						rule.getPrefix(), e.getMessage(), e);
			}
		}

		log.info("文件生命周期清理执行完成");
	}

	/**
	 * 手动触发清理
	 *
	 * @return 清理结果统计
	 */
	public CleanupResult executeManualCleanup() {
		if (!lifecycleProperties.isEnabled()) {
			return CleanupResult.skipped("文件生命周期清理未启用");
		}

		List<FileLifecycleProperties.LifecycleRule> rules = lifecycleProperties.getRules();
		if (rules == null || rules.isEmpty()) {
			return CleanupResult.skipped("文件生命周期清理规则为空");
		}

		IFileStorage storage = resolveStorage();
		if (storage == null) {
			return CleanupResult.failed("无法获取文件存储实例");
		}

		String bucketName = lifecycleProperties.getBucket();
		boolean dryRun = lifecycleProperties.isDryRun();
		CleanupResult result = new CleanupResult();

		for (FileLifecycleProperties.LifecycleRule rule : rules) {
			try {
				processRuleWithStats(storage, bucketName, rule, dryRun, result);
			} catch (Exception e) {
				log.error("文件生命周期清理规则执行失败, prefix={}: {}",
						rule.getPrefix(), e.getMessage(), e);
				result.addError(rule.getPrefix(), e.getMessage());
			}
		}

		return result;
	}

	/**
	 * 处理单条生命周期规则
	 *
	 * @param storage    文件存储实例
	 * @param bucketName 存储桶名称
	 * @param rule       生命周期规则
	 * @param dryRun     是否仅模拟执行
	 */
	private void processRule(IFileStorage storage, String bucketName,
			FileLifecycleProperties.LifecycleRule rule, boolean dryRun) {
		String prefix = rule.getPrefix();
		long maxAgeMillis = rule.getMaxAgeDays() * 24L * 60L * 60L * 1000L;
		long cutoffTime = System.currentTimeMillis() - maxAgeMillis;

		log.info("处理生命周期规则: prefix={}, maxAgeDays={}", prefix, rule.getMaxAgeDays());

		// 列出指定前缀下的所有对象
		String cursor = null;
		int scannedCount = 0;
		int deletedCount = 0;
		int skippedCount = 0;

		while (true) {
			com.njydsz.pmis.common.file.domain.ListObjectsResult listResult =
					storage.listObjects(bucketName, prefix, cursor, 1000);

			if (listResult == null || listResult.getObjects() == null || listResult.getObjects().isEmpty()) {
				break;
			}

			for (com.njydsz.pmis.common.file.domain.ObjectMetadata obj : listResult.getObjects()) {
				scannedCount++;
				long lastModified = obj.getLastModified() != null
						? obj.getLastModified().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
						: 0;

				if (lastModified > 0 && lastModified < cutoffTime) {
					if (dryRun) {
						log.info("[DryRun] 跳过删除过期文件: objectName={}, lastModified={}",
								obj.getObjectName(), obj.getLastModified());
						skippedCount++;
					} else {
						try {
							storage.delete(bucketName, obj.getObjectName());
							deletedCount++;
							log.debug("删除过期文件: objectName={}", obj.getObjectName());
						} catch (Exception e) {
							log.error("删除过期文件失败: objectName={}, error={}", obj.getObjectName(), e.getMessage());
						}
					}
				} else {
					skippedCount++;
				}
			}

			cursor = listResult.getNextCursor();
			if (cursor == null || cursor.isEmpty()) {
				break;
			}
		}

		log.info("生命周期规则执行完成: prefix={}, scanned={}, deleted={}, skipped={}",
				prefix, scannedCount, deletedCount, skippedCount);
	}

	/**
	 * 处理单条规则并统计结果
	 */
	private void processRuleWithStats(IFileStorage storage, String bucketName,
			FileLifecycleProperties.LifecycleRule rule, boolean dryRun, CleanupResult result) {
		String prefix = rule.getPrefix();
		long maxAgeMillis = rule.getMaxAgeDays() * 24L * 60L * 60L * 1000L;
		long cutoffTime = System.currentTimeMillis() - maxAgeMillis;

		String cursor = null;

		while (true) {
			com.njydsz.pmis.common.file.domain.ListObjectsResult listResult =
					storage.listObjects(bucketName, prefix, cursor, 1000);

			if (listResult == null || listResult.getObjects() == null || listResult.getObjects().isEmpty()) {
				break;
			}

			for (com.njydsz.pmis.common.file.domain.ObjectMetadata obj : listResult.getObjects()) {
				long lastModified = obj.getLastModified() != null
						? obj.getLastModified().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
						: 0;

				if (lastModified > 0 && lastModified < cutoffTime) {
					if (dryRun) {
						result.incrementSkipped();
					} else {
						try {
							storage.delete(bucketName, obj.getObjectName());
							result.incrementDeleted();
						} catch (Exception e) {
							result.addError(obj.getObjectName(), e.getMessage());
						}
					}
				}
				result.incrementScanned();
			}

			cursor = listResult.getNextCursor();
			if (cursor == null || cursor.isEmpty()) {
				break;
			}
		}
	}

	/**
	 * 解析文件存储实例
	 *
	 * @return 文件存储实例
	 */
	private IFileStorage resolveStorage() {
		if (fileStorage != null) {
			return fileStorage;
		}
		if (storageFactory != null) {
			return storageFactory.getStorage();
		}
		return null;
	}

	/**
	 * 清理结果统计
	 */
	@Data
	public static class CleanupResult {

		private int scannedCount = 0;
		private int deletedCount = 0;
		private int skippedCount = 0;
		private boolean success = true;
		private String message;
		private List<String> errors = new ArrayList<>();

		public CleanupResult() {
		}

		public static CleanupResult skipped(String message) {
			CleanupResult result = new CleanupResult();
			result.success = true;
			result.message = message;
			return result;
		}

		public static CleanupResult failed(String message) {
			CleanupResult result = new CleanupResult();
			result.success = false;
			result.message = message;
			return result;
		}

		public void incrementScanned() {
			scannedCount++;
		}

		public void incrementDeleted() {
			deletedCount++;
		}

		public void incrementSkipped() {
			skippedCount++;
		}

		public void addError(String path, String error) {
			errors.add(path + ": " + error);
		}
	}

	/**
	 * 文件生命周期规则配置
	 *
	 * <p>配置前缀: {@code remi.file.lifecycle}
	 */
	@Data
	@Component
	@ConfigurationProperties(prefix = "remi.file.lifecycle")
	public static class FileLifecycleProperties {

		/**
		 * 是否启用文件生命周期管理
		 */
		private boolean enabled = false;

		/**
		 * 定时任务 cron 表达式
		 */
		private String cron = "0 0 2 * * ?";

		/**
		 * 存储桶名称
		 */
		private String bucket;

		/**
		 * 生命周期规则列表
		 */
		private List<LifecycleRule> rules = new ArrayList<>();

		/**
		 * 是否仅模拟执行（不实际删除）
		 */
		private boolean dryRun = false;

		/**
		 * 生命周期规则
		 */
		@Data
		public static class LifecycleRule {

			/**
			 * 文件路径前缀匹配（如 "temp/", "logs/"）
			 */
			private String prefix;

			/**
			 * 最大保留天数，超过此天数的文件将被清理
			 */
			private int maxAgeDays;

			/**
			 * 清理动作，当前仅支持 delete
			 */
			private String action = "delete";
		}
	}
}

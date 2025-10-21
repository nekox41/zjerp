package top.aameow.autobackup.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.autobackup.config.AlistProperties;
import top.aameow.autobackup.config.UploadProperties;
import top.aameow.autobackup.service.CloudBackupService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileBackupScheduler {
    private final CloudBackupService cloudBackupService;
    private final AlistProperties alistProperties;
    private final UploadProperties uploadProperties;

    private static final Logger log = LoggerFactory.getLogger(FileBackupScheduler.class);

    @Scheduled(cron = "${upload.cron}")
    public void backupFiles() {
        File uploadDir = new File(RuoYiConfig.getProfile());
        List<File> targetFiles = new ArrayList<>();

        if (!uploadDir.exists() || !uploadDir.isDirectory()) {
            log.error("上传目录不存在或不是目录：{}", uploadProperties.getDirecotry());
            return;
        }

        getFiles(uploadDir, targetFiles);

        if (targetFiles.isEmpty()) {
            log.info("上传目录为空，无需备份");
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        log.info("开始备份 {} 个文件", targetFiles.size());

        for (File file : targetFiles) {
            if (file.isFile()) {
                // TODO 先从数据库获取记录，判断是否已经备份
                String relativePath = getRelativePath(uploadDir, file);
                String targetPath = alistProperties.getBackupPath() + "/" + relativePath;

                if (cloudBackupService.uploadFile(file, targetPath)) {
                    log.info("文件备份成功: {}", file.getName());
                    successCount++;
                    cloudBackupService.insertRecord(file);
                }
            } else {
                log.error("文件备份失败: {}", file.getName());
                failureCount++;
            }
        }

        log.info("文件备份完成 - 成功: {}, 失败: {}", successCount, failureCount);
    }

    private void getFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    result.add(file);
                } else if (file.isDirectory()) {
                    getFiles(file, result);
                }
            }
        }
    }

    private String getRelativePath(File baseDir, File file) {
        String basePath = baseDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (filePath.startsWith(basePath)) {
            String relativePath = filePath.substring(basePath.length());
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            return relativePath;
        }
        return file.getName();
    }
}

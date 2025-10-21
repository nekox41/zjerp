package top.aameow.autobackup.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.aameow.autobackup.config.AlistProperties;
import top.aameow.autobackup.config.UploadProperties;
import top.aameow.autobackup.domain.BackupRecord;
import top.aameow.autobackup.mapper.CloudBackupMapper;
import top.aameow.common.utils.DateUtils;
import top.aameow.common.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudBackupService {
    private final AlistProperties alistProperties;
    private final UploadProperties uploadProperties;
    private final RestTemplate restTemplate;
    private final CloudBackupMapper mapper;
    @Getter
    private String cachedToken;
    @Getter
    private long tokenExpireTime;

    private static final long TOKEN_EXPIRE_BUFFER = 1000 * 60 * 30;
    private static final Logger log = LoggerFactory.getLogger(CloudBackupService.class);

    // 登录获取 Token
    public void login() {
        String url = alistProperties.getApiUrl();
        String username = alistProperties.getUsername();
        String password = alistProperties.getPassword();

        if (url == null || username == null || password == null) {
            throw new RuntimeException("AList 配置信息不完整: apiUrl=" + url + ", username=" + username + ", password=" + password);
        }

        url += "/api/auth/login";

        Map<String, String> requestBody = Map.of(
                "username", alistProperties.getUsername(),
                "password", alistProperties.getPassword()
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestBody, Map.class);
            if (response.getBody() != null && "200".equals(response.getBody().get("code").toString())) {
                cachedToken = (String) response.getBody().get("data");
                tokenExpireTime = System.currentTimeMillis() + TOKEN_EXPIRE_BUFFER;
                log.info("成功刷新 alist token.");
                return;
            }
            throw new RuntimeException("登录云盘失败：" + response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("登录云盘时发生错误", e);
        }
    }

    private void ensureValidToken() {
        if (cachedToken == null || System.currentTimeMillis() > tokenExpireTime) {
            login();
        }
    }

    public boolean uploadFile(File file, String targetPath) {
        ensureValidToken();

        String url = alistProperties.getApiUrl() + "/api/fs/put";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set("Authorization", cachedToken);
            headers.set("File-Path", targetPath);
            headers.setContentLength(file.length());

            Resource resource = new FileSystemResource(file);
            HttpEntity<Resource> requestEntity = new HttpEntity<>(resource, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Map.class);

            if (response.getBody() != null && "200".equals(response.getBody().get("code").toString())) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("上传文件失败：" + file.getName(), e);
            return false;
        }
    }

    public void insertRecord(File file) {
        BackupRecord record = new BackupRecord();
        record.setFileName(file.getName());
        try {
            record.setFileSHA256(FileUtils.calculateSHA256(file));
        } catch (IOException e) {
            log.error("插入备份记录时出错", e);
        }
        record.setUploadTime(DateUtils.getTime());
        mapper.insertRecord(record);
    }

    // TODO 获取记录
    public boolean recordIsExist(File file) {
        // 先获取文件的SHA256
        String sha256 = null;
        try {
            sha256 = FileUtils.calculateSHA256(file);
        } catch (IOException e) {
            log.error("获取文件SHA256失败", e);
        }
        return mapper.getRecord(sha256) != null;
    }
}

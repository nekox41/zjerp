package top.aameow.autobackup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "alist")
@Data
public class AlistProperties {
    private String apiUrl;
    private String username;
    private String password;
    private String backupPath;
}

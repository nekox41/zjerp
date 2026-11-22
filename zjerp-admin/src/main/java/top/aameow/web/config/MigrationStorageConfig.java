package top.aameow.web.config;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.aameow.migration.StagedSourceStore;

@Configuration
public class MigrationStorageConfig
{
    @Bean
    public StagedSourceStore stagedSourceStore(
            @Value("${migration.staging-directory:${user.home}/.zjerp/private/migration-staging}") String directory,
            @Value("${migration.max-source-bytes:1073741824}") long maxBytes)
    {
        try
        {
            return new StagedSourceStore(Path.of(directory), maxBytes);
        }
        catch (IOException | IllegalArgumentException e)
        {
            throw new BeanCreationException("Unable to initialize private migration staging", e);
        }
    }
}

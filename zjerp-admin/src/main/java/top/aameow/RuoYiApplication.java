package top.aameow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(RuoYiApplication.class, args);
        System.out.println("""
                 _____      _     _____   ____    ____ \s
                |__  /     | |   | ____| |  _ \\  |  _ \\\s
                  / /   _  | |   |  _|   | |_) | | |_) |
                 / /_  | |_| |   | |___  |  _ <  |  __/\s
                /____|  \\___/    |_____| |_| \\_\\ |_|   \s
                                                       \s""");
    }
}

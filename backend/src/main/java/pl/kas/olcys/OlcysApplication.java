package pl.kas.olcys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.kas.olcys.config.AiProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class OlcysApplication {

    public static void main(String[] args) {
        SpringApplication.run(OlcysApplication.class, args);
    }
}

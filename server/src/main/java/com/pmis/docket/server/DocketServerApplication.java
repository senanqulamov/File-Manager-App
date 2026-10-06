package com.pmis.docket.server;

import com.pmis.docket.server.config.DocketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DocketProperties.class)
public class DocketServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocketServerApplication.class, args);
    }
}

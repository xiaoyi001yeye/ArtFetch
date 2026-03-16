package com.artfetch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.artfetch.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ArtFetchApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArtFetchApplication.class, args);
    }
}

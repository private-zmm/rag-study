package com.ragstudy.knowledge.framework;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QdrantVectorProperties.class)
public class QdrantVectorConfig {
}

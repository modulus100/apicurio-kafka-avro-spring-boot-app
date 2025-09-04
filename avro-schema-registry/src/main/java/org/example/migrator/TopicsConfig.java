package org.example.migrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "schema")
public record TopicsConfig(List<Topic> topics) {
    public record Topic(String name, String directory) { }
}

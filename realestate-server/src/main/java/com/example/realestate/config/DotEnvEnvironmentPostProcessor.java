package com.example.realestate.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时加载 realestate-server/.env（KEY=VALUE）为低优先级属性源。
 * 系统环境变量优先级更高，可覆盖 .env；.env 本身已加入 .gitignore。
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "localDotEnv";
    private static final List<String> SEARCH_PATHS = List.of(".env", "../.env");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> props = new HashMap<>();
        for (String path : SEARCH_PATHS) {
            Path file = Path.of(path).toAbsolutePath().normalize();
            if (Files.isRegularFile(file)) {
                loadFile(file, props);
                break;
            }
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
    }

    private void loadFile(Path file, Map<String, Object> props) {
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                props.putIfAbsent(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 .env 失败: " + file, e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}

package com.example.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 应用配置常量
 *
 * 统一管理配置文件路径和配置加载
 */
public class AppConfig {

    /** 配置文件路径 */
    public static final String CONFIG_FILE = "application_bak.properties";

    /** 配置项 key */
    public static final String OPENAI_API_KEY = "openai.api.key";
    public static final String OPENAI_API_BASE_URL = "openai.api.base-url";
    public static final String OPENAI_MODEL_NAME = "openai.model.name";

    /** 默认值 */
    public static final String DEFAULT_API_KEY = "sk-your-api-key-here";
    public static final String DEFAULT_BASE_URL = "https://api.openai.com";
    public static final String DEFAULT_MODEL_NAME = "gpt-4o-mini";

    /**
     * 加载配置文件
     */
    public static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream is = AppConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
            }
        }
        return props;
    }

    /**
     * 检查 API Key 是否已配置
     */
    public static boolean isApiKeyConfigured(String apiKey) {
        return apiKey != null && !apiKey.equals(DEFAULT_API_KEY);
    }
}

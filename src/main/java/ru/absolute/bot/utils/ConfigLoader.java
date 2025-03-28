package ru.absolute.bot.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try {
            // Загрузка из файла config.properties (для отладки на локальной машине)
            var inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties");
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                // Загрузка из переменных окружения
                Map<String, String> envProperties = System.getProperties().stringPropertyNames().stream()
                        .filter(key -> key.startsWith("BOT_"))
                        .collect(Collectors.toMap(key -> key.substring(4).toLowerCase(), System::getProperty));
                envProperties.forEach(properties::setProperty);
            }


        } catch (IOException e) {
            throw new RuntimeException("Ошибка при загрузке конфигурации", e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}

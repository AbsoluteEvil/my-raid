package ru.absolute.bot.services;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.absolute.bot.models.Boss;

import ru.absolute.bot.dao.BossDao;
import ru.absolute.bot.dao.ItemsDao;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BossService {
    private final BossDao bossDao;
    private final ItemsDao itemsDao;

    // Кеш для хранения списка боссов
    private List<Boss> bossesCache;
    private long lastBossesUpdateTime = 0;
    private static final long BOSSES_CACHE_EXPIRATION_TIME = 30 * 60 * 1000; // 30 минут

    // Кеш для хранения предметов (ID -> Name)
    private Map<String, String> itemsMap;
    private long lastItemsUpdateTime = 0;
    private static final long ITEMS_CACHE_EXPIRATION_TIME = 30 * 60 * 1000; // 30 минут

    public BossService(BossDao bossDao, ItemsDao itemsDao) {
        this.bossDao = bossDao;
        this.itemsDao = itemsDao;
        initializeCache(); // Инициализируем кеш при создании сервиса
    }

    /**
     * Инициализирует кеш при старте сервиса.
     */
    private void initializeCache() {
        try {
            // Загружаем всех боссов в кеш
            this.bossesCache = bossDao.getAllBosses();
            this.lastBossesUpdateTime = System.currentTimeMillis();

            // Загружаем все предметы в кеш
            this.itemsMap = itemsDao.getAllItems();
            this.lastItemsUpdateTime = System.currentTimeMillis();

            log.info("Кеш инициализирован. Загружено {} боссов и {} предметов.", bossesCache.size(), itemsMap.size());
        } catch (IOException e) {
            log.error("Ошибка при инициализации кеша", e);
            throw new RuntimeException("Не удалось инициализировать кеш", e);
        }
    }

    /**
     * Получает всех боссов из кеша.
     */
    public List<Boss> getAllBosses() {
        return bossesCache;
    }

    /**
     * Находит босса по полному совпадению имени.
     */
    public Boss findBossByName(String bossName) {
        return bossesCache.stream()
                .filter(boss -> boss.getName().equalsIgnoreCase(bossName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Находит боссов по частичному совпадению имени.
     */
    public List<Boss> findBossesByName(String partialName) {
        String lowerCasePartialName = partialName.toLowerCase();
        return bossesCache.stream()
                .filter(boss -> boss.getName().toLowerCase().contains(lowerCasePartialName))
                .collect(Collectors.toList());
    }

    /**
     * Обновляет время убийства босса и обновляет кеш.
     */
    public void updateKillTime(String bossName) throws IOException {
        Boss boss = findBossByName(bossName);
        if (boss != null) {
            // Обновляем время убийства
            boss.setKillTime(LocalDateTime.now().withNano(0));

            // Обновляем данные в Google Sheets
            bossDao.updateBoss(boss);

            // Обновляем кеш
            bossesCache = bossDao.getAllBosses();
            lastBossesUpdateTime = System.currentTimeMillis();

            log.info("Время убийства босса {} обновлено. Кеш обновлен.", bossName);
        } else {
            throw new IllegalArgumentException("Босс с именем " + bossName + " не найден.");
        }
    }

    /**
     * Получает кэшированную карту предметов (ID -> Name).
     */
    public Map<String, String> getItemsMap() {
        long currentTime = System.currentTimeMillis();
        if (itemsMap == null || currentTime - lastItemsUpdateTime > ITEMS_CACHE_EXPIRATION_TIME) {
            try {
                // Обновляем кеш
                this.itemsMap = itemsDao.getAllItems();
                this.lastItemsUpdateTime = currentTime;
                log.info("Кэш предметов обновлен. Загружено {} записей.", itemsMap.size());
            } catch (IOException e) {
                log.error("Ошибка при обновлении кэша предметов", e);
                throw new RuntimeException("Не удалось обновить кэш предметов", e);
            }
        }
        return itemsMap;
    }

}
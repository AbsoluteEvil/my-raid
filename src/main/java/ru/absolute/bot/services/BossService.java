package ru.absolute.bot.services;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.absolute.bot.models.Boss;

import ru.absolute.bot.dao.BossDao;
import ru.absolute.bot.dao.ItemsDao;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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
        long currentTime = System.currentTimeMillis();
        if (bossesCache == null || currentTime - lastBossesUpdateTime > BOSSES_CACHE_EXPIRATION_TIME) {
            try {
                log.info("Кеш боссов устарел. Перезагружаем...");
                this.bossesCache = bossDao.getAllBosses();
                this.lastBossesUpdateTime = currentTime;
            } catch (IOException e) {
                log.error("Ошибка при обновлении кеша боссов", e);
                throw new RuntimeException("Не удалось обновить кеш боссов", e);
            }
        }
        return bossesCache;
    }

    /**
     * Находит босса по полному совпадению имени.
     */
    public Boss findBossByName(String bossName) {
        return getAllBosses().stream()
                .filter(boss -> boss.getName().equalsIgnoreCase(bossName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Находит боссов по частичному совпадению имени.
     */
    public List<Boss> findBossesByName(String partialName) {
        String lowerCasePartialName = partialName.toLowerCase();
        return getAllBosses().stream()
                .filter(boss -> boss.getName().toLowerCase().contains(lowerCasePartialName))
                .collect(Collectors.toList());
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

    /**
     * Обновляет время убийства босса с указанным временем и обновляет кеш.
     * @param bossName имя босса
     * @param killTime время убийства (Instant)
     * @throws IOException если произошла ошибка при обновлении данных
     * @throws IllegalArgumentException если босс не найден
     */
    public void updateKillTime(String bossName, Instant killTime) throws IOException {
        Boss boss = findBossByName(bossName);
        if (boss != null) {
            // Конвертируем Instant в LocalDateTime (используем системный часовой пояс)
            LocalDateTime killDateTime = LocalDateTime.ofInstant(killTime, ZoneId.systemDefault());

            // Обновляем время убийства (убираем наносекунды для точности)
            boss.setKillTime(killDateTime.withNano(0));

            // Обновляем данные в Google Sheets
            bossDao.updateBoss(boss);

            // Обновляем кеш
            bossesCache = bossDao.getAllBosses();
            lastBossesUpdateTime = System.currentTimeMillis();

            log.info("Время убийства босса {} обновлено на {}. Кеш обновлен.",
                    bossName, killDateTime);
        } else {
            throw new IllegalArgumentException("Босс с именем " + bossName + " не найден.");
        }
    }

    public List<String> getCheckerLoginsForBoss(int bossId) {
        try {
            Boss boss = findBossById(bossId);
            if (boss == null || boss.getCheckersId() == null) {
                return Collections.emptyList();
            }
            return getCheckerLogins(boss.getCheckersId());
        } catch (Exception e) {
            log.error("Ошибка получения проверяющих для босса {}", bossId, e);
            return Collections.emptyList();
        }
    }

    private List<String> getCheckerLogins(String checkersId) {
        if (checkersId == null || checkersId.isEmpty() || checkersId.equals("{}")) {
            return Collections.emptyList();
        }

        List<String> logins = new ArrayList<>();
        String[] ids = checkersId.replaceAll("[{}]", "").split(",");

        for (String idStr : ids) {
            try {
                int id = Integer.parseInt(idStr.trim());
                String login = null;
                try {
                    login = bossDao.findCheckerLoginById(id);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (login != null && !login.isEmpty()) {
                    logins.add(login);
                }
            } catch (NumberFormatException e) {
                log.warn("Некорректный ID проверяющего: {}", idStr);
            }
        }
        return logins;
    }

    private Boss findBossById(int bossId) {
        return bossesCache.stream()
                .filter(b -> b.getId() == bossId)
                .findFirst()
                .orElse(null);
    }

}
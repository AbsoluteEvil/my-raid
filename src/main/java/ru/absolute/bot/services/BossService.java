package ru.absolute.bot.services;

import lombok.SneakyThrows;
import ru.absolute.bot.models.Boss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class BossService {
    private final GoogleSheetsService sheetsService;

    @SneakyThrows
    public BossService() {
        this.sheetsService = new GoogleSheetsService();
    }

    @SneakyThrows
    public void updateKillTime(String bossName) {
        Boss boss = findBossByName(bossName);
        if (boss != null) {
            boss.setKillTime(LocalDateTime.now().withNano(0));
            sheetsService.updateBoss(boss);
        } else {
            throw new IllegalArgumentException("Босс с именем " + bossName + " не найден.");
        }
    }

    /**
     * Находит боссов по полному совпадению имени.
     */
    public Boss findBossByName (String bossName) {
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



    @SneakyThrows
    public List<Boss> getAllBosses() {
        return sheetsService.getAllBosses();
    }

    @SneakyThrows
    public List<String> getDropsByBossName(String bossName) {
        return sheetsService.getDropsByBossName(bossName);
    }


}
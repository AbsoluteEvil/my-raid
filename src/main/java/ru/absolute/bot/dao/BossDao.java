package ru.absolute.bot.dao;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.clients.GoogleSheetsClient;
import ru.absolute.bot.models.Boss;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BossDao {
    private static final Logger logger = LoggerFactory.getLogger(BossDao.class);
    private final GoogleSheetsClient sheetsClient;
    private final String sheetName;

    public BossDao(GoogleSheetsClient sheetsClient, String sheetName) {
        this.sheetsClient = sheetsClient;
        this.sheetName = sheetName;
    }

    public List<Boss> getAllBosses() throws IOException {
        logger.info("Получение списка боссов из таблицы...");
        ValueRange response = sheetsClient.getValues(sheetName);

        List<Boss> bosses = new ArrayList<>();
        for (int i = 1; i < response.getValues().size(); i++) { // Пропустить заголовок
            List<Object> row = response.getValues().get(i);
            Boss boss = createBossFromRow(row);
            if (boss != null) {
                bosses.add(boss);
            }
        }

        logger.info("Успешно получено {} боссов.", bosses.size());
        return bosses;
    }

    public void updateBoss(Boss boss) throws IOException {
        ValueRange response = sheetsClient.getValues(sheetName);
        int rowIndex = -1;

        // Ищем строку с ID босса
        for (int i = 1; i < response.getValues().size(); i++) {
            List<Object> row = response.getValues().get(i);
            if (row.get(0).toString().equals(String.valueOf(boss.getId()))) {
                rowIndex = i + 1; // Индекс строки в Google Sheets начинается с 1
                break;
            }
        }

        if (rowIndex == -1) {
            throw new IllegalArgumentException("Босс с ID " + boss.getId() + " не найден в таблице.");
        }

        // Формируем тело запроса для обновления строки
        ValueRange body = new ValueRange()
                .setValues(List.of(
                        List.of(
                                boss.getId(),
                                boss.getName(),
                                boss.getLevel(),
                                boss.getKillTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        )
                ));

        // Обновляем строку
        sheetsClient.updateValues(sheetName + "!A" + rowIndex, body);
    }

    private Boss createBossFromRow(List<Object> row) {
        try {
            int id = Integer.parseInt(row.get(0).toString());
            String name = row.get(1).toString();
            int level = row.size() > 2 ? Integer.parseInt(row.get(2).toString()) : 0;
            LocalDateTime killTime = row.size() > 3 && !row.get(3).toString().isEmpty()
                    ? LocalDateTime.parse(row.get(3).toString().trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null;
            List<String> itemList = row.size() > 4
                    ? Arrays.asList(row.get(4).toString().replace("[", "").replace("]", "").split(","))
                    : Collections.emptyList();
            String location = row.size() > 5 ? row.get(5).toString() : "";
            String checkersId = row.size() > 6 ? row.get(6).toString() : "{}"; // Значение по умолчанию

            return new Boss(id, name, level, killTime, itemList, location,checkersId);
        } catch (Exception e) {
            logger.error("Ошибка при создании Boss из строки {}: {}", row, e.getMessage());
            return null;
        }
    }

    public List<String> getDropsByBossName(String bossName) throws IOException {
        ValueRange response = sheetsClient.getValues(sheetName);

        List<String> drops = new ArrayList<>();
        for (int i = 1; i < response.getValues().size(); i++) {
            List<Object> row = response.getValues().get(i);
            if (row.get(0).toString().equalsIgnoreCase(bossName)) {
                drops.add(row.get(1).toString());
            }
        }
        return drops;
    }

    public String findCheckerLoginById(int id) throws IOException {
        String range = "checkers!A2:B";
        ValueRange response = sheetsClient.getValues(range);

        for (List<Object> row : response.getValues()) {
            if (row.size() >= 2) {
                try {
                    int currentId = Integer.parseInt(row.get(0).toString());
                    if (currentId == id) {
                        return row.get(1).toString();
                    }
                } catch (NumberFormatException e) {
                    logger.error("Ошибка при поиске палалки из строки {}: {}", row, e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }


}
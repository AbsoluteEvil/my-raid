package ru.absolute.bot.dao;

import com.google.api.services.sheets.v4.model.ValueRange;
import ru.absolute.bot.clients.GoogleSheetsClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemsDao {
    private final GoogleSheetsClient sheetsClient;
    private final String sheetName;

    public ItemsDao(GoogleSheetsClient sheetsClient, String sheetName) {
        this.sheetsClient = sheetsClient;
        this.sheetName = sheetName;
    }

    /**
     * Получает все предметы из таблицы.
     */
    public Map<String, String> getAllItems() throws IOException {
        ValueRange response = sheetsClient.getValues(sheetName);

        Map<String, String> itemsMap = new HashMap<>();
        for (int i = 1; i < response.getValues().size(); i++) { // Пропускаем заголовок
            List<Object> row = response.getValues().get(i);
            if (row.size() >= 2) { // Проверяем, что строка содержит ID и название
                String itemId = row.get(0).toString();
                String itemName = row.get(1).toString();
                itemsMap.put(itemId, itemName);
            }
        }

        return itemsMap;
    }
}
package ru.absolute.bot.dao;

import com.google.api.services.sheets.v4.model.ValueRange;
import ru.absolute.bot.clients.GoogleSheetsClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EventDao {
    private static final Logger logger = LoggerFactory.getLogger(EventDao.class);
    private final GoogleSheetsClient sheetsClient;
    private final String sheetName;

    public EventDao(GoogleSheetsClient sheetsClient, String sheetName) {
        this.sheetsClient = sheetsClient;
        this.sheetName = sheetName;
    }

    public List<Event> getAllEvents() throws IOException {
        logger.info("Получение списка событий из таблицы...");
        ValueRange response = sheetsClient.getValues(sheetName);

        if (response == null || response.getValues() == null) {
            logger.error("Таблица пуста или данные не найдены.");
            return Collections.emptyList();
        }

        logger.info("Получено строк: {}", response.getValues().size());
        List<Event> events = new ArrayList<>();
        for (int i = 1; i < response.getValues().size(); i++) { // Пропустить заголовок
            List<Object> row = response.getValues().get(i);
            Event event = createEventFromRow(row);
            if (event != null) {
                events.add(event);
            }
        }

        logger.info("Успешно получено {} событий.", events.size());
        return events;
    }

    public void createEvent(Event event) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(List.of(
                        List.of(
                                event.getId(),
                                event.getDate().toString(),
                                event.getBossName(),
                                event.getDrop(),
                                String.join(",", event.getMembers()),
                                event.getNumberOfMembers(),
                                event.getStatus().toString()
                        )
                ));

        sheetsClient.appendValues(sheetName + "!A1", body);
    }

    private Event createEventFromRow(List<Object> row) {
        try {
            String id = row.get(0).toString();
            LocalDate date = LocalDate.parse(row.get(1).toString());
            String bossName = row.get(2).toString();
            String drop = row.get(3).toString();
            List<String> members = List.of(row.get(4).toString().split(","));
            int numberOfMembers = row.size() > 5 ? Integer.parseInt(row.get(5).toString()) : 0;
            EventStatus status = row.size() > 6 ? EventStatus.valueOf(row.get(6).toString()) : EventStatus.IN_PROGRESS;

            return new Event(id, date, bossName, drop, members, numberOfMembers, status);
        } catch (Exception e) {
            logger.error("Ошибка при создании Event из строки {}: {}", row, e.getMessage());
            return null;
        }
    }


    public Event findEventById(String id) throws IOException {
        List<Event> events = getAllEvents();
        for (Event event : events) {
            if (event.getId().equals(id)) {
                return event;
            }
        }
        return null; // Если событие не найдено
    }

    public int findRowIndexById(String eventId) throws IOException {
        // Получаем все строки из таблицы
        ValueRange response = sheetsClient.getValues(sheetName);
        List<List<Object>> values = response.getValues();

        // Проверяем, что таблица не пуста
        if (values == null || values.isEmpty()) {
            throw new IOException("Таблица событий пуста.");
        }

        // Ищем строку с нужным ID
        for (int i = 1; i < values.size(); i++) { // Начинаем с 1, чтобы пропустить заголовок
            List<Object> row = values.get(i);
            if (row.get(0).toString().equals(eventId)) { // Проверяем ID в первом столбце
                return i + 1; // Возвращаем номер строки (начиная с 1, так как строки в Google Sheets нумеруются с 1)
            }
        }

        throw new IOException("Событие с ID " + eventId + " не найдено.");
    }

    public void updateEvent(Event event) throws IOException {
        logger.info("Обновление события с ID: {}", event.getId());

        // Находим номер строки по ID события
        int rowIndex = findRowIndexById(event.getId());
        logger.info("Найдена строка для обновления: {}", rowIndex);

        // Формируем данные для обновления
        List<List<Object>> values = Arrays.asList(
                Arrays.asList(
                        event.getId(),
                        event.getDate().toString(),
                        event.getBossName(),
                        String.join(",", event.getDrop()),
                        String.join(",", event.getMembers()),
                        event.getMembers().size(),
                        event.getStatus().toString()
                )
        );

        // Создаем тело запроса
        ValueRange body = new ValueRange().setValues(values);
        logger.info("Данные для обновления: {}", body.getValues());

        // Определяем диапазон для обновления
        String range = sheetName + "!A" + rowIndex + ":G" + rowIndex;
        logger.info("Диапазон для обновления: {}", range);

        // Вызываем метод для обновления данных в таблице
        sheetsClient.updateValues(range, body);
        logger.info("Событие успешно обновлено.");
    }


}
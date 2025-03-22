package ru.absolute.bot.services;

import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EventService {
    @Getter
    private final GoogleSheetsService sheetsService;
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsService.class);

    @SneakyThrows
    public EventService() {
        this.sheetsService = new GoogleSheetsService();
    }

    @SneakyThrows
    public String createEvent(String bossName, String drop, List<String> members) {
        // Создаем объект события
        Event event = new Event(
                generateId(),
                LocalDate.now(),
                bossName,
                drop != null ? drop : "",
                members != null ? members : List.of(),
                members != null ? members.size() : 0,
                EventStatus.IN_PROGRESS
        );

        try {
            // Логируем информацию о создаваемом событии
            logger.info("Создание события для босса: {}", bossName);
            logger.info("Дропы: {}", drop);
            logger.info("Участники: {}", members);

            // Пытаемся создать событие в Google Sheets
            sheetsService.createEvent(event);

            // Логируем успешное создание события
            logger.info("Событие успешно создано с ID: {}", event.getId());

            // Возвращаем ID созданного события
            return event.getId();
        } catch (Exception e) {
            // Логируем ошибку с деталями
            logger.error("Ошибка при создании события для босса: {}", bossName, e);
            logger.error("Дропы: {}", drop);
            logger.error("Участники: {}", members);
            logger.error("Исключение: {}", e.getMessage(), e);

            // Пробрасываем исключение дальше
            throw e;
        }
    }

    @SneakyThrows
    public void editEvent(String eventId, EventStatus status, List<String> addMembers, List<String> removeMembers) {
        Event event = sheetsService.findEventById(eventId);
        if (event != null) {
            if (status != null) {
                event.setStatus(status);
            }
            if (addMembers != null) {
                addMembers.forEach(event::addMember);
            }
            if (removeMembers != null) {
                removeMembers.forEach(event::removeMember);
            }
            sheetsService.updateEvent(event);
        }
    }

    private String generateId() {
        return String.valueOf(System.currentTimeMillis());
    }

    @SneakyThrows
    public List<Event> getEventsByStatus(EventStatus status) {
        try {
            logger.info("Получение событий со статусом: {}", status);
            List<Event> events = sheetsService.getEventsByStatus(status);

            // Если события не найдены, возвращаем пустой список
            if (events == null) {
                logger.info("События не найдены.");
                return Collections.emptyList();
            }

            logger.info("Найдено {} событий.", events.size());
            return events;
        } catch (Exception e) {
            logger.error("Ошибка при получении событий", e);
            return Collections.emptyList(); // Возвращаем пустой список в случае ошибки
        }
    }
}
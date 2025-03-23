package ru.absolute.bot.services;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.dao.EventDao;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class EventService {
    private final EventDao eventDao;

    public EventService(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    /**
     * Создает новое событие.
     */
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
            log.info("Создание события для босса: {}", bossName);
            log.info("Дропы: {}", drop);
            log.info("Участники: {}", members);

            // Пытаемся создать событие в Google Sheets
            eventDao.createEvent(event);

            // Логируем успешное создание события
            log.info("Событие успешно создано с ID: {}", event.getId());

            // Возвращаем ID созданного события
            return event.getId();
        } catch (Exception e) {
            // Логируем ошибку с деталями
            log.error("Ошибка при создании события для босса: {}", bossName, e);
            log.error("Дропы: {}", drop);
            log.error("Участники: {}", members);
            log.error("Исключение: {}", e.getMessage(), e);

            // Пробрасываем исключение дальше
            throw new RuntimeException("Не удалось создать событие", e);
        }
    }

    /**
     * Редактирует существующее событие.
     */
    public void editEvent(String eventId, EventStatus status, List<String> addMembers, List<String> removeMembers) {
        try {
            Event event = eventDao.findEventById(eventId);
            if (event != null) {
                if (status != null) {
                    event.setStatus(status);
                }
                if (addMembers != null) {
                    addMembers.forEach(event::addMember); // Используем addMember
                }
                if (removeMembers != null) {
                    removeMembers.forEach(event::removeMember); // Используем removeMember
                }
                eventDao.updateEvent(event);
            } else {
                log.warn("Событие с ID {} не найдено.", eventId);
            }
        } catch (Exception e) {
            log.error("Ошибка при редактировании события с ID: {}", eventId, e);
            throw new RuntimeException("Не удалось редактировать событие", e);
        }
    }

    /**
     * Получает события по статусу.
     */
    public List<Event> getEventsByStatus(EventStatus status) {
        try {
            log.info("Получение событий со статусом: {}", status);
            List<Event> allEvents  = eventDao.getAllEvents();

            // Если события не найдены, возвращаем пустой список
            if (allEvents  == null) {
                log.info("События не найдены.");
                return Collections.emptyList();
            }

            log.info("Найдено {} событий.", allEvents.size());
            // Фильтруем события по статусу
            return allEvents.stream()
                    .filter(event -> event.getStatus() == status)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Ошибка при получении событий по статусу: {}", status, e);
            throw new RuntimeException("Не удалось получить события по статусу", e);
        }
    }

    /**
     * Получает название предмета по его ID.
     */
    public String getItemNameById(String itemId) {
        try {
            return getItemNameById(itemId);
        } catch (Exception e) {
            log.error("Ошибка при получении названия предмета по ID: {}", itemId, e);
            return itemId; // Возвращаем ID, если не удалось получить название
        }
    }

    public Event findEventById(String id) throws IOException {
        return eventDao.findEventById(id);
    }

    /**
     * Генерирует уникальный ID для события.
     */
    private String generateId() {
        return String.valueOf(System.currentTimeMillis());
    }

    public void updateEvent(Event event) throws IOException {
        eventDao.updateEvent(event);
    }
}
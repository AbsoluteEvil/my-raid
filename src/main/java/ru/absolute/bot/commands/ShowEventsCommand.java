package ru.absolute.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.services.EventService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ShowEventsCommand {
    private static final Logger logger = LoggerFactory.getLogger(ShowEventsCommand.class);
    private final EventService eventService = new EventService();

    // Метод для обработки команды /show_events
    public void handle(SlashCommandInteractionEvent event) {
        try {
            // Подтверждаем выполнение команды (deferReply)
            event.deferReply().queue();

            // Получаем статус, если он передан
            OptionMapping statusOption = event.getOption("status");
            EventStatus status = EventStatus.IN_PROGRESS; // По умолчанию фильтруем по IN_PROGRESS
            if (statusOption != null) {
                try {
                    status = EventStatus.valueOf(statusOption.getAsString().toUpperCase());
                } catch (IllegalArgumentException e) {
                    event.getHook().sendMessage("Неверный статус. Используйте IN_PROGRESS или DONE.").setEphemeral(true).queue();
                    return;
                }
            }

            // Отправляем первую страницу событий
            sendEventPage(event, status, 0);

        } catch (Exception e) {
            logger.error("Ошибка при выполнении команды /show_events", e);
            event.getHook().sendMessage("Произошла ошибка при получении списка событий.").setEphemeral(true).queue();
        }
    }

    // Метод для отправки страницы событий
    public void sendEventPage(SlashCommandInteractionEvent event, EventStatus status, int page) {
        try {
            // Подтверждаем выполнение команды (deferReply)
            event.deferReply().queue();

            // Отправляем первую страницу событий
            sendEventPageInternal(event, null, status, page);

        } catch (Exception e) {
            logger.error("Ошибка при выполнении команды /show_events", e);
            event.getHook().sendMessage("Произошла ошибка при получении списка событий.").setEphemeral(true).queue();
        }
    }

    // Метод для обработки ButtonInteractionEvent (навигация по страницам)
    public void sendEventPageForButton(ButtonInteractionEvent event, EventStatus status, int page) {
        try {
            // Получаем события
            List<Event> events = eventService.getEventsByStatus(status);
            if (events == null || events.isEmpty()) {
                event.getHook().sendMessage("Событий не найдено.").setEphemeral(true).queue();
                return;
            }

            // Разбиваем события на страницы (по 5 событий на страницу)
            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) events.size() / pageSize);

            // Проверяем, что запрошенная страница существует
            if (page < 0 || page >= totalPages) {
                event.getHook().sendMessage("Неверная страница.").setEphemeral(true).queue();
                return;
            }

            // Вычисляем начальный и конечный индексы для текущей страницы
            int start = page * pageSize;
            int end = Math.min(start + pageSize, events.size());

            // Отправляем каждое событие отдельным сообщением с кнопками
            for (int i = start; i < end; i++) {
                Event eventItem = events.get(i);
                sendEventMessage(eventItem, null, event);

                // Добавляем задержку в 1 секунду между сообщениями
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Ошибка при добавлении задержки", e);
                }
            }

            // Добавляем кнопки для навигации по страницам
            if (totalPages > 1) {
                Button previousButton = Button.secondary("events_previous:" + status + ":" + page, "Previous").withDisabled(page == 0);
                Button nextButton = Button.secondary("events_next:" + status + ":" + page, "Next").withDisabled(page == totalPages - 1);

                event.getHook().sendMessage("Навигация:")
                        .setActionRow(previousButton, nextButton)
                        .queue();
            }

        } catch (Exception e) {
            logger.error("Ошибка при отправке страницы событий", e);
            event.getHook().sendMessage("Произошла ошибка при отправке страницы событий.").setEphemeral(true).queue();
        }
    }

    // Внутренний метод для обработки обоих типов событий
    private void sendEventPageInternal(SlashCommandInteractionEvent slashEvent, ButtonInteractionEvent buttonEvent, EventStatus status, int page) {
        try {
            // Получаем события
            List<Event> events = eventService.getEventsByStatus(status);
            if (events == null || events.isEmpty()) {
                sendReply("Событий не найдено.", slashEvent, buttonEvent);
                return;
            }

            // Разбиваем события на страницы (по 5 событий на страницу)
            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) events.size() / pageSize);

            // Проверяем, что запрошенная страница существует
            if (page < 0 || page >= totalPages) {
                sendReply("Неверная страница.", slashEvent, buttonEvent);
                return;
            }

            // Добавляем заголовок страницы
            String pageHeader = "───────────────────────\n**Страница " + (page + 1) + " из " + totalPages + "**\n───────────────────────";
            if (slashEvent != null) {
                slashEvent.getHook().sendMessage(pageHeader).queue();
            } else if (buttonEvent != null) {
                buttonEvent.getHook().sendMessage(pageHeader).queue();
            }

            // Вычисляем начальный и конечный индексы для текущей страницы
            int start = page * pageSize;
            int end = Math.min(start + pageSize, events.size());

            // Отправляем каждое событие отдельным сообщением с кнопками
            for (int i = start; i < end; i++) {
                Event eventItem = events.get(i);
                sendEventMessage(eventItem, slashEvent, buttonEvent);
            }

            // Добавляем кнопки для навигации по страницам
            if (totalPages > 1) {
                Button previousButton = Button.secondary("events_previous:" + status + ":" + page, "◀").withDisabled(page == 0);
                Button nextButton = Button.secondary("events_next:" + status + ":" + page, "▶").withDisabled(page == totalPages - 1);

                String navigationMessage = "Навигация:";
                if (slashEvent != null) {
                    slashEvent.getHook().sendMessage(navigationMessage)
                            .setActionRow(previousButton, nextButton)
                            .queue();
                } else if (buttonEvent != null) {
                    buttonEvent.getHook().sendMessage(navigationMessage)
                            .setActionRow(previousButton, nextButton)
                            .queue();
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка при отправке страницы событий", e);
            sendReply("Произошла ошибка при отправке страницы событий.", slashEvent, buttonEvent);
        }
    }

    // Метод для отправки сообщения с событием и кнопками
    private void sendEventMessage(Event eventItem, SlashCommandInteractionEvent slashEvent, ButtonInteractionEvent buttonEvent) {
        String drop = eventItem.getDrop();
        String dropNames = "";

        if (!drop.isEmpty()) {
            try {
                // Получаем названия дропов по их ID
                List<String> dropIds = Arrays.asList(drop.split(","));
                dropNames = dropIds.stream()
                        .map(id -> {
                            try {
                                return eventService.getSheetsService().getItemNameById(id);
                            } catch (Exception e) {
                                logger.error("Ошибка при получении названия дропа по ID: " + id, e);
                                return id; // Возвращаем ID, если не удалось получить название
                            }
                        })
                        .collect(Collectors.joining(", "));
            } catch (Exception e) {
                logger.error("Ошибка при обработке дропов", e);
                dropNames = drop; // Возвращаем исходный список ID, если произошла ошибка
            }
        }

        String message = "**Дата:** " + eventItem.getDate() + "\n" +
                "**ID:** " + eventItem.getId() + "\n" +
                "**Босс:** " + eventItem.getBossName() + "\n" +
                "**Дроп:** " + (dropNames.isEmpty() ? "Нет дропа" : dropNames) + "\n" +
                "**Участники:** " + String.join(", ", eventItem.getMembers());

        // Создаем кнопки для управления событием
        Button editButton = Button.primary("edit_event:" + eventItem.getId(), "Edit");
        Button closeButton = Button.danger("close_event:" + eventItem.getId(), "Close");

        // Отправляем сообщение с кнопками
        if (slashEvent != null) {
            slashEvent.getHook().sendMessage(message)
                    .setActionRow(editButton, closeButton)
                    .queue(); // Асинхронная отправка
        } else if (buttonEvent != null) {
            buttonEvent.getHook().sendMessage(message)
                    .setActionRow(editButton, closeButton)
                    .queue(); // Асинхронная отправка
        }
    }

    // Метод для отправки простого ответа
    private void sendReply(String message, SlashCommandInteractionEvent slashEvent, ButtonInteractionEvent buttonEvent) {
        if (slashEvent != null) {
            slashEvent.getHook().sendMessage(message).setEphemeral(true).queue();
        } else if (buttonEvent != null) {
            buttonEvent.getHook().sendMessage(message).setEphemeral(true).queue();
        }
    }
}
package ru.absolute.bot.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.commands.CreateEventCommand;
import ru.absolute.bot.commands.ShowEventsCommand;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.services.EventService;

import java.util.List;

public class ButtonHandler extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ButtonHandler.class);
    private final CreateEventCommand createEventCommand;
    private final EventService eventService;
    private final ShowEventsCommand showEventsCommand;

    public ButtonHandler(CreateEventCommand createEventCommand, EventService eventService, ShowEventsCommand showEventsCommand) {
        this.createEventCommand = createEventCommand;
        this.eventService = eventService;
        this.showEventsCommand = showEventsCommand;
    }

//    @Override
//    public void onButtonInteraction(ButtonInteractionEvent event) {
//        String buttonId = event.getComponentId();
//        logger.info("Нажата кнопка с ID: {}", buttonId); // Логируем ID кнопки
//        if (buttonId.startsWith("ok_")) {
//            // Обработка кнопки ОК
//            String bossName = buttonId.replace("ok_", "");
//            event.reply("Время убийства босса " + bossName + " зафиксировано.")
//                    .setEphemeral(true)
//                    .queue();
//            event.editComponents().queue(); // Делаем кнопки неактивными
//        } else if (buttonId.startsWith("create_event_")) {
//            // Обработка кнопки Создать событие
//            String bossName = buttonId.replace("create_event_", "");
//            event.reply("Создание события для босса " + bossName + "...")
//                    .setEphemeral(true)
//                    .queue();
//        } else if (buttonId.startsWith("confirm_drops:")) {
//            createEventCommand.handleConfirmButtonInteraction(event);
//        } else if (buttonId.startsWith("skip_drops:")) {
//            createEventCommand.handleSkipButtonInteraction(event);
//        } else if (buttonId.startsWith("edit_event:")) {
//            // Обработка кнопки Edit
//            String eventId = buttonId.replace("edit_event:", "");
//            handleEditEvent(event, eventId);
//        } else if (buttonId.startsWith("close_event:")) {
//            // Обработка кнопки Close
//            String eventId = buttonId.replace("close_event:", "");
//            handleCloseEvent(event, eventId);
//        } else if (buttonId.startsWith("events_previous:")) {
//            // Обработка кнопки Previous
//            event.deferEdit().queue(); // Подтверждаем взаимодействие
//            String[] parts = buttonId.split(":");
//            EventStatus status = EventStatus.valueOf(parts[1]); // Извлекаем статус
//            int currentPage = Integer.parseInt(parts[2]); // Извлекаем номер страницы
//            showEventsCommand.sendEventPageForButton(event, status, currentPage - 1); // Переходим на предыдущую страницу
//        } else if (buttonId.startsWith("events_next:")) {
//            // Обработка кнопки Next
//            event.deferEdit().queue(); // Подтверждаем взаимодействие
//            String[] parts = buttonId.split(":");
//            EventStatus status = EventStatus.valueOf(parts[1]); // Извлекаем статус
//            int currentPage = Integer.parseInt(parts[2]); // Извлекаем номер страницы
//            showEventsCommand.sendEventPageForButton(event, status, currentPage + 1); // Переходим на следующую страницу
//        }
//    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (buttonId.startsWith("skip_drops:")) {
            createEventCommand.handleSkipButtonInteraction(event);
        } else if (buttonId.startsWith("confirm_drops:")) {
            createEventCommand.handleConfirmButtonInteraction(event);
        } else if (buttonId.startsWith("ok_")) {
            // Обработка кнопки ОК
            String bossName = buttonId.replace("ok_", "");

            // Отправляем ответ и делаем кнопки неактивными
            event.editMessage("Время убийства босса " + bossName + " зафиксировано.")
                    .setComponents() // Убираем все кнопки
                    .queue();
        } else if (buttonId.startsWith("create_event_")) {
            // Обработка кнопки Создать событие
            String bossName = buttonId.replace("create_event_", "");

            // Затем вызываем команду create_event
            createEventCommand.handleButtonEvent(event, bossName);
        } else if (buttonId.startsWith("edit_event:")) {
            // Обработка кнопки Edit
            String eventId = buttonId.replace("edit_event:", "");
            handleEditEvent(event, eventId);
        } else if (buttonId.startsWith("close_event:")) {
            // Обработка кнопки Close
            String eventId = buttonId.replace("close_event:", "");
            handleCloseEvent(event, eventId);
        } else if (buttonId.startsWith("events_previous:")) {
            // Обработка кнопки Previous
            String[] parts = buttonId.split(":");
            EventStatus status = EventStatus.valueOf(parts[1]); // Извлекаем статус
            int currentPage = Integer.parseInt(parts[2]); // Извлекаем номер страницы
            showEventsCommand.sendEventPageForButton(event, status, currentPage - 1); // Используем ShowEventsCommand
        } else if (buttonId.startsWith("events_next:")) {
            // Обработка кнопки Next
            String[] parts = buttonId.split(":");
            EventStatus status = EventStatus.valueOf(parts[1]); // Извлекаем статус
            int currentPage = Integer.parseInt(parts[2]); // Извлекаем номер страницы
            showEventsCommand.sendEventPageForButton(event, status, currentPage + 1); // Используем ShowEventsCommand
        }
    }

    private void handleEditEvent(ButtonInteractionEvent event, String eventId) {
        try {
            // Получаем событие по ID
            Event eventItem = eventService.getSheetsService().findEventById(eventId);
            if (eventItem == null) {
                event.reply("Событие не найдено.").setEphemeral(true).queue();
                return;
            }

            // Отправляем сообщение с возможностью редактирования
            event.reply("Редактирование события: " + eventItem.getId())
                    .addActionRow(
                            Button.primary("edit_drop:" + eventItem.getId(), "Изменить дроп"),
                            Button.primary("edit_members:" + eventItem.getId(), "Изменить участников")
                    )
                    .queue();

        } catch (Exception e) {
            logger.error("Ошибка при редактировании события", e);
            event.reply("Произошла ошибка при редактировании события.").setEphemeral(true).queue();
        }
    }

    private void handleCloseEvent(ButtonInteractionEvent event, String eventId) {
        try {
            // Закрываем событие (меняем статус на DONE)
            eventService.editEvent(eventId, EventStatus.DONE, null, null);

            // Отправляем подтверждение
            event.reply("Событие " + eventId + " закрыто.").setEphemeral(true).queue();

        } catch (Exception e) {
            logger.error("Ошибка при закрытии события", e);
            event.reply("Произошла ошибка при закрытии события.").setEphemeral(true).queue();
        }
    }
}
package ru.absolute.bot.handlers;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class ButtonHandler extends ListenerAdapter {
    private final CreateEventCommand createEventCommand;
    private final EventService eventService;
    private final ShowEventsCommand showEventsCommand;

    public ButtonHandler(CreateEventCommand createEventCommand, EventService eventService, ShowEventsCommand showEventsCommand) {
        this.createEventCommand = createEventCommand;
        this.eventService = eventService;
        this.showEventsCommand = showEventsCommand;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        try {
            if (buttonId.startsWith("skip_drops:")) {
                createEventCommand.handleSkipButtonInteraction(event);
            } else if (buttonId.startsWith("confirm_drops:")) {
                createEventCommand.handleConfirmButtonInteraction(event);
            } else if (buttonId.startsWith("ok_")) {
                handleOkButton(event, buttonId);
            } else if (buttonId.startsWith("create_event_")) {
                handleCreateEventButton(event, buttonId);
            } else if (buttonId.startsWith("edit_event:")) {
                handleEditEventButton(event, buttonId);
            } else if (buttonId.startsWith("close_event:")) {
                handleCloseEventButton(event, buttonId);
            } else {
                log.warn("Неизвестная кнопка: {}", buttonId);
                event.reply("Неизвестная команда.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке кнопки: {}", buttonId, e);
            event.reply("Произошла ошибка при обработке команды.").setEphemeral(true).queue();
        }
    }

    /**
     * Обрабатывает кнопку "ОК".
     */
    private void handleOkButton(ButtonInteractionEvent event, String buttonId) {
        String bossName = buttonId.replace("ok_", "");
        event.editMessage("Время убийства босса " + bossName + " зафиксировано.")
                .setComponents() // Убираем все кнопки
                .queue();
    }

    /**
     * Обрабатывает кнопку "Создать событие".
     */
    private void handleCreateEventButton(ButtonInteractionEvent event, String buttonId) {
        String bossName = buttonId.replace("create_event_", "");
        createEventCommand.handleButtonEvent(event, bossName);
    }

    /**
     * Обрабатывает кнопку "Редактировать событие".
     */
    private void handleEditEventButton(ButtonInteractionEvent event, String buttonId) {
        String eventId = buttonId.replace("edit_event:", "");
        try {
            Event eventItem = eventService.findEventById(eventId);
            if (eventItem == null) {
                event.reply("Событие не найдено.").setEphemeral(true).queue();
                return;
            }

            event.reply("Редактирование события: " + eventItem.getId())
                    .addActionRow(
                            Button.primary("edit_drop:" + eventItem.getId(), "Изменить дроп"),
                            Button.primary("edit_members:" + eventItem.getId(), "Изменить участников")
                    )
                    .queue();
        } catch (Exception e) {
            log.error("Ошибка при редактировании события", e);
            event.reply("Произошла ошибка при редактировании события.").setEphemeral(true).queue();
        }
    }

    /**
     * Обрабатывает кнопку "Закрыть событие".
     */
    private void handleCloseEventButton(ButtonInteractionEvent event, String buttonId) {
        String eventId = buttonId.replace("close_event:", "");
        try {
            eventService.editEvent(eventId, EventStatus.DONE, null, null);
            event.reply("Событие " + eventId + " закрыто.").setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("Ошибка при закрытии события", e);
            event.reply("Произошла ошибка при закрытии события.").setEphemeral(true).queue();
        }
    }


}
package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.services.EventService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class EditEventCommand {
    private final EventService eventService;

    public EditEventCommand(EventService eventService) {
        this.eventService = eventService;
    }

    public void handle(SlashCommandInteractionEvent event) {
        log.info("Команда /edit_event вызвана.");
        // Получаем ID события
        OptionMapping idOption = event.getOption("id");
        if (idOption == null) {
            event.reply("Пожалуйста, укажите ID события.").setEphemeral(true).queue();
            return;
        }
        String eventId = idOption.getAsString();

        // Получаем событие по ID
        Event eventToEdit;
        try {
            eventToEdit = eventService.findEventById(eventId);
        } catch (IOException e) {
            event.reply("Ошибка при поиске события.").setEphemeral(true).queue();
            return;
        }

        if (eventToEdit == null) {
            event.reply("Событие с ID **" + eventId + "** не найдено.").setEphemeral(true).queue();
            return;
        }

        // Редактируем статус (если указан)
        OptionMapping statusOption = event.getOption("set_status");
        if (statusOption != null) {
            String statusStr = statusOption.getAsString();
            try {
                EventStatus newStatus = EventStatus.valueOf(statusStr.toUpperCase());
                eventToEdit.setStatus(newStatus);
            } catch (IllegalArgumentException e) {
                event.reply("Неверный статус события. Используйте IN_PROGRESS или DONE.").setEphemeral(true).queue();
                return;
            }
        }

        // Добавляем участников (если указаны)
        OptionMapping addMembersOption = event.getOption("add_member");
        if (addMembersOption != null) {
            String[] membersToAdd = addMembersOption.getAsString().split(",");
            List<String> currentMembers = new ArrayList<>(eventToEdit.getMembers()); // Создаем изменяемую копию
            currentMembers.addAll(Arrays.asList(membersToAdd));
            eventToEdit.setMembers(currentMembers);
        }

        // Удаляем участников (если указаны)
        OptionMapping deleteMembersOption = event.getOption("delete_member");
        if (deleteMembersOption != null) {
            String[] membersToDelete = deleteMembersOption.getAsString().split(",");
            List<String> currentMembers = new ArrayList<>(eventToEdit.getMembers()); // Создаем изменяемую копию
            currentMembers.removeAll(Arrays.asList(membersToDelete));
            eventToEdit.setMembers(currentMembers);
        }

        // Обновляем событие в базе данных
        try {
            eventService.updateEvent(eventToEdit);
            event.reply("Событие с ID **" + eventId + "** успешно обновлено.").setEphemeral(true).queue();
        } catch (IOException e) {
            event.reply("Ошибка при обновлении события.").setEphemeral(true).queue();
        }
    }

    public void handleAutocomplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery option = event.getFocusedOption();
        log.info("Обработка автодополнения для параметра: {}", option);
        try {
            if (option.getName().equals("id")) {
                // Автодополнение для ID событий со статусом IN_PROGRESS
                List<Event> inProgressEvents = eventService.getEventsByStatus(EventStatus.IN_PROGRESS);
                log.info("Найдено событий IN_PROGRESS: {}", inProgressEvents.size());
                List<Command.Choice> options = inProgressEvents.stream()
                        .map(evt -> new Command.Choice(
                                evt.getBossName() + " (" + evt.getDate() + ")", // Подсказка: имя босса и дата
                                evt.getId() // Значение: ID события
                        ))
                        .collect(Collectors.toList());
                log.info("Предложено вариантов: {}", options.size());
                event.replyChoices(options).queue();
            }
            else if (option.getName().equals("set_status")) {
                // Предлагаем варианты статусов
                List<Command.Choice> options = Arrays.stream(EventStatus.values())
                        .map(status -> new Command.Choice(status.toString(), status.toString()))
                        .collect(Collectors.toList());

                event.replyChoices(options).queue();
            }
            else if (option.getName().equals("delete_member")) {
                // Автодополнение для участников
                String eventId = event.getOption("id").getAsString();
                Event eventToEdit = eventService.findEventById(eventId);

                if (eventToEdit != null) {
                    List<Command.Choice> options = eventToEdit.getMembers().stream()
                            .map(member -> new Command.Choice(member, member))
                            .collect(Collectors.toList());

                    event.replyChoices(options).queue();
                }
            }
        } catch (IOException e) {
            log.error("Ошибка при обработке автодополнения", e);
        }

    }
}
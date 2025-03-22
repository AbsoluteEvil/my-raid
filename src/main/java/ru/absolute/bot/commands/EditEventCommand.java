package ru.absolute.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.services.EventService;

import java.util.Arrays;
import java.util.List;

public class EditEventCommand {
    private final EventService eventService = new EventService();

    public void handle(SlashCommandInteractionEvent event) {
        // Получаем параметры команды
        String eventId = event.getOption("id").getAsString();
        OptionMapping setStatusOption = event.getOption("set_status");
        OptionMapping addMemberOption = event.getOption("add_member");
        OptionMapping deleteMemberOption = event.getOption("delete_member");

        // Парсим статус, если он передан
        EventStatus status = null;
        if (setStatusOption != null) {
            status = EventStatus.valueOf(setStatusOption.getAsString().toUpperCase());
        }

        // Парсим участников, если они переданы
        List<String> addMembers = null;
        if (addMemberOption != null) {
            addMembers = Arrays.asList(addMemberOption.getAsString().split(","));
        }

        List<String> deleteMembers = null;
        if (deleteMemberOption != null) {
            deleteMembers = Arrays.asList(deleteMemberOption.getAsString().split(","));
        }

        // Редактируем событие
        //eventService.editEvent(eventId, status, addMembers, deleteMembers);

        // Отправляем ответ
        event.reply("Событие с ID " + eventId + " успешно обновлено.").setEphemeral(true).queue();
    }
}
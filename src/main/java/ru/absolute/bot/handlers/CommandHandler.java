package ru.absolute.bot.handlers;

import lombok.SneakyThrows;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.commands.CreateEventCommand;
import ru.absolute.bot.commands.EditEventCommand;
import ru.absolute.bot.commands.KillCommand;
import ru.absolute.bot.commands.ShowCommand;
import ru.absolute.bot.commands.ShowEventsCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ru.absolute.bot.services.GoogleSheetsService;


public class CommandHandler extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsService.class);
    private final KillCommand killCommand = new KillCommand();
    private final CreateEventCommand createEventCommand = new CreateEventCommand();
    private final EditEventCommand editEventCommand = new EditEventCommand();
    private final ShowCommand showCommand = new ShowCommand();
    private final ShowEventsCommand showEventsCommand = new ShowEventsCommand();


    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        switch (commandName) {
            case "k":
                killCommand.handle(event);
                break;
            case "create_event":
                createEventCommand.handle(event);
                break;
            case "edit_event":
                editEventCommand.handle(event);
                break;
            case "show":
                showCommand.handle(event);
                break;
            case "show_events":
                showEventsCommand.handle(event);
                break;
            default:
                event.reply("Неизвестная команда.").setEphemeral(true).queue();
                break;
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        switch (commandName) {
            case "k":
                killCommand.handleAutocomplete(event);
                break;
            case "create_event":
                createEventCommand.handleAutocomplete(event);
                break;
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        logger.info("Получено событие StringSelectInteraction с ID: {}", event.getComponentId());
        if (event.getComponentId().startsWith("drop_selection:")) {
            logger.info("Обработка выбора дропа для босса: {}", event.getComponentId());
            createEventCommand.handleSelectMenu(event);
        }
    }

}
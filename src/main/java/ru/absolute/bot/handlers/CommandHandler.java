package ru.absolute.bot.handlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ru.absolute.bot.commands.CreateEventCommand;
import ru.absolute.bot.commands.EditEventCommand;
import ru.absolute.bot.commands.KillCommand;
import ru.absolute.bot.commands.ShowCommand;
import ru.absolute.bot.commands.ShowEventsCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@Slf4j
public class CommandHandler extends ListenerAdapter {
    private final KillCommand killCommand;
    private final CreateEventCommand createEventCommand;
    private final EditEventCommand editEventCommand;
    private final ShowCommand showCommand;
    private final ShowEventsCommand showEventsCommand;

    public CommandHandler(
            KillCommand killCommand,
            CreateEventCommand createEventCommand,
            EditEventCommand editEventCommand,
            ShowCommand showCommand,
            ShowEventsCommand showEventsCommand
    ) {
        this.killCommand = killCommand;
        this.createEventCommand = createEventCommand;
        this.editEventCommand = editEventCommand;
        this.showCommand = showCommand;
        this.showEventsCommand = showEventsCommand;
    }

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
        log.info("Обработка автодополнения для команды: {}", commandName);
        switch (commandName) {
            case "edit_event":
                editEventCommand.handleAutocomplete(event);
                break;
            case "create_event":
                createEventCommand.handleAutocomplete(event);
                break;
            case "k":
                killCommand.handleAutocomplete(event);
                break;
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        log.info("Получено событие StringSelectInteraction с ID: {}", event.getComponentId());
        if (event.getComponentId().startsWith("drop_selection:")) {
            log.info("Обработка выбора дропа для босса: {}", event.getComponentId());
            createEventCommand.handleSelectMenu(event);
        }
    }
}
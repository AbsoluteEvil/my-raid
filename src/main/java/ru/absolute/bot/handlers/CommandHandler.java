package ru.absolute.bot.handlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ru.absolute.bot.commands.*;

import java.util.ArrayList;

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
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            switch (event.getName()) {
                case "k" -> killCommand.handle(event);
                case "create_event" -> createEventCommand.handle(event);
                case "edit_event" -> handleEditEventCommand(event);
                case "show" -> showCommand.handle(event);
                case "show_events" -> showEventsCommand.handle(event);
                default -> {
                    event.reply("Неизвестная команда.").setEphemeral(true).queue();
                    log.warn("Получена неизвестная команда: {}", event.getName());
                }
            }
        } catch (Exception e) {
            log.error("Ошибка обработки команды: ", e);
            event.reply("Произошла ошибка при выполнении команды.").setEphemeral(true).queue();
        }
    }

    private void handleEditEventCommand(SlashCommandInteractionEvent event) {
        if ("edit_members".equals(event.getSubcommandName())) {
            editEventCommand.handleEditMembersCommand(event);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        try {
            String commandName = event.getName();
            log.debug("Обработка автодополнения для команды: {}", commandName);

            switch (commandName) {
                case "create_event":
                    createEventCommand.handleAutocomplete(event);
                    break;
                case "k":
                    killCommand.handleAutocomplete(event);
                    break;
                case "edit_event":  // Добавляем обработку для edit_event
                    editEventCommand.handleAutocomplete(event);
                    break;
                default:
                    log.warn("Автодополнение не поддерживается для команды: {}", commandName);
                    event.replyChoices(new ArrayList<>()).queue();
            }
        } catch (Exception e) {
            log.error("Ошибка обработки автодополнения: ", e);
            event.replyChoices(new ArrayList<>()).queue();
        }
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        try {
            if ("select_members".equals(event.getComponentId())) {
                editEventCommand.handleMemberSelection(event);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки выбора участников: ", e);
            event.reply("Произошла ошибка при обработке выбора.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        try {
            if (event.getComponentId().startsWith("drop_selection:")) {
                createEventCommand.handleSelectMenu(event);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки выбора: ", e);
            event.reply("Произошла ошибка при обработке выбора.").setEphemeral(true).queue();
        }
    }
}
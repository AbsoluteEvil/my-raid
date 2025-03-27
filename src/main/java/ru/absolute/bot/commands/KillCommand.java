package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.utils.TimeUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class KillCommand {
    private final BossService bossService;

    public KillCommand(BossService bossService) {
        this.bossService = bossService;
    }

    /**
     * Обрабатывает команду /kill.
     */
    public void handle(SlashCommandInteractionEvent event) {
        // Немедленно подтверждаем получение команды
        event.deferReply().queue(hook -> {
            try {
                String bossName = Objects.requireNonNull(event.getOption("boss_name")).getAsString();
                OptionMapping timeOption = event.getOption("time");

                Instant killTime;
                if (timeOption != null) {
                    try {
                        killTime = TimeUtils.parseKillTime(timeOption.getAsString());
                    } catch (IllegalArgumentException e) {
                        editHookWithError(hook, e.getMessage());
                        return;
                    }
                } else {
                    killTime = Instant.now();
                }

                bossService.updateKillTime(bossName, killTime);

                Button okButton = Button.success("ok_" + bossName, "ОК");
                Button createEventButton = Button.primary("create_event_" + bossName, "Создать событие");

                hook.editOriginal("Босс " + bossName + " убит. Время убийства: " + TimeUtils.formatTime(killTime))
                        .setActionRow(okButton, createEventButton)
                        .queue(
                                success -> log.info("Успешно обработано убийство босса {}", bossName),
                                error -> log.error("Ошибка при отправке ответа", error)
                        );
            } catch (Exception e) {
                log.error("Ошибка при обновлении времени убийства босса", e);
                editHookWithError(hook, "Произошла ошибка при обновлении времени убийства босса.");
            }
        }, error -> {
            log.error("Не удалось подтвердить команду /kill", error);
            event.getHook().editOriginal("Произошла ошибка при обработке команды.").queue();
        });
    }

    /**
     * Обрабатывает автозаполнение для команды /kill.
     */
    public void handleAutocomplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery option = event.getFocusedOption();
        if (option.getName().equals("boss_name")) {
            String userInput = option.getValue().toLowerCase();
            try {
                List<Command.Choice> choices = bossService.findBossesByName(userInput).stream()
                        .map(boss -> new Command.Choice(
                                String.format("%s [%d], %s", boss.getName(), boss.getLevel(), boss.getLocation()),
                                boss.getName()))
                        .limit(25)
                        .collect(Collectors.toList());
                event.replyChoices(choices).queue(
                        null,
                        error -> log.error("Ошибка при отправке автозаполнения", error)
                );
            } catch (Exception e) {
                log.error("Ошибка при обработке автозаполнения для boss_name", e);
                event.replyChoices().queue(
                        null,
                        error -> log.error("Ошибка при отправке пустого автозаполнения", error)
                );
            }
        }
    }

    private void editHookWithError(InteractionHook hook, String errorMessage) {
        hook.editOriginal(errorMessage)
                .queue(
                        success -> {},
                        error -> log.error("Не удалось отправить сообщение об ошибке", error)
                );
    }
}
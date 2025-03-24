package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ru.absolute.bot.services.BossService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ru.absolute.bot.utils.TimeUtils;

import java.time.Instant;
import java.util.List;
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
        String bossName = event.getOption("boss_name").getAsString();
        OptionMapping timeOption = event.getOption("time");

        Instant killTime;
        if (timeOption != null) {
            try {
                killTime = TimeUtils.parseKillTime(timeOption.getAsString());
            } catch (IllegalArgumentException e) {
                event.reply(e.getMessage()).setEphemeral(true).queue();
                return;
            }
        } else {
            killTime = Instant.now();
        }

        try {
            bossService.updateKillTime(bossName, killTime);

            Button okButton = Button.success("ok_" + bossName, "ОК");
            Button createEventButton = Button.primary("create_event_" + bossName, "Создать событие");

            event.reply("Босс " + bossName + " убит. Время убийства: " + TimeUtils.formatTime(killTime))
                    .addActionRow(okButton, createEventButton)
                    .queue();
        } catch (Exception e) {
            log.error("Ошибка при обновлении времени убийства босса {}", bossName, e);
            event.reply("Произошла ошибка при обновлении времени убийства босса.").setEphemeral(true).queue();
        }
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
                event.replyChoices(choices).queue();
            } catch (Exception e) {
                log.error("Ошибка при обработке автозаполнения для boss_name", e);
                event.replyChoices().queue();
            }
        }
    }
}
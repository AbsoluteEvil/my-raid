package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import ru.absolute.bot.services.BossService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

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
        try {
            // Обновляем время убийства босса
            bossService.updateKillTime(bossName);

            // Создаем кнопки
            Button okButton = Button.success("ok_" + bossName, "ОК"); // Кнопка ОК
            Button createEventButton = Button.primary("create_event_" + bossName, "Создать событие"); // Кнопка Создать событие

            // Отправляем сообщение с кнопками
            event.reply("Босс " + bossName + " убит. Время убийства обновлено.")
                    .addActionRow(okButton, createEventButton) // Добавляем кнопки
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
            String userInput = option.getValue().toLowerCase(); // Получаем значение опции
            try {
                List<Command.Choice> choices = bossService.findBossesByName(userInput).stream()
                        .map(boss -> new Command.Choice(boss.getName(), boss.getName()))
                        .limit(25) // Ограничиваем количество вариантов до 25
                        .collect(Collectors.toList());
                event.replyChoices(choices).queue();
            } catch (Exception e) {
                log.error("Ошибка при обработке автозаполнения для boss_name", e);
                event.replyChoices().queue(); // Пустой ответ в случае ошибки
            }
        }
    }
}
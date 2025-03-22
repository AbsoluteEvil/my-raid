package ru.absolute.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.GoogleSheetsService;
import ru.absolute.bot.utils.TimeUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class KillCommand {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsService.class);
    private final BossService bossService = new BossService();

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
            event.reply("Произошла ошибка при обновлении времени убийства босса.").setEphemeral(true).queue();
        }
    }

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
                e.printStackTrace(); // Логируем ошибку для отладки
                event.replyChoices().queue(); // Пустой ответ в случае ошибки
            }
        }
    }
}
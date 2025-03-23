package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ShowEventsCommand {
    private final EventService eventService;
    private final BossService bossService;

    public ShowEventsCommand(EventService eventService, BossService bossService) {
        this.eventService = eventService;
        this.bossService = bossService;
    }

    public void handle(SlashCommandInteractionEvent event) {
        // Получаем статус события из команды (если указан)
        OptionMapping statusOption = event.getOption("status");
        EventStatus status;

        if (statusOption == null) {
            // Если статус не указан, используем IN_PROGRESS по умолчанию
            status = EventStatus.IN_PROGRESS;
        } else {
            // Пытаемся получить статус из команды
            String statusStr = statusOption.getAsString();
            try {
                status = EventStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                event.reply("Неверный статус события. Используйте IN_PROGRESS или DONE.").setEphemeral(true).queue();
                return;
            }
        }

        // Получаем события по статусу
        List<Event> events = eventService.getEventsByStatus(status);

        // Сортируем по дате
        events.sort(Comparator.comparing(Event::getDate));

        // Если событий нет, сообщаем об этом
        if (events.isEmpty()) {
            event.reply("Событий со статусом **" + status + "** не найдено.").setEphemeral(true).queue();
            return;
        }

        // Создаем embed для красивого вывода
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("События со статусом: " + status);
        embed.setColor(status == EventStatus.IN_PROGRESS ? Color.GREEN : Color.GRAY); // Цвет в зависимости от статуса

        // Добавляем каждое событие в embed
        for (Event ev : events) {
            // Получаем названия дропов
            List<String> dropNames = getDropNames(ev.getDrop(), bossService);

            // Форматируем дропы в блоки
            String formattedDrops = formatDropsIntoBlocks(dropNames, 5); // 5 дропов в блоке

            // Форматируем дату
            String formattedDate = ev.getDate().format(DateTimeFormatter.ofPattern("dd.MM"));

            // Эмодзи для статуса
            String statusEmoji = status == EventStatus.IN_PROGRESS ? "⏳" : "✅";

            // Формируем информацию о событии
            String eventInfo = String.format(
                    "%s **Событие #%s**\n" +
                            "**Дата:** %s\n" +
                            "**Босс:** %s\n" +
                            "**Дроп:**\n%s\n" +
                            "**Участники:** %s\n" +
                            "**Кол-во участников:** %d\n" +
                            "\u200B", // Разделитель (невидимый символ)
                    statusEmoji, // Эмодзи для статуса
                    ev.getId(),
                    formattedDate, // Используем отформатированную дату
                    ev.getBossName(),
                    formattedDrops,
                    String.join(", ", ev.getMembers()),
                    ev.getNumberOfMembers()
            );

            // Добавляем событие в embed
            embed.addField("\u200B", eventInfo, false); // Используем невидимый символ для разделения
        }

        // Отправляем embed в ответ
        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Получает названия дропов по их ID.
     */
    private List<String> getDropNames(String dropIds, BossService bossService) {
        Map<String, String> itemsMap = bossService.getItemsMap(); // Используем кэш предметов
        List<String> dropNames = new ArrayList<>();

        // Предположим, что dropIds — это строка вида "393,394,415,416,1938,1939,316,317,2030,956"
        String[] ids = dropIds.split(",");
        for (String id : ids) {
            String itemName = itemsMap.get(id.trim());
            if (itemName != null) {
                dropNames.add(itemName);
            } else {
                log.warn("Не найдено название для дропа с ID: {}", id);
            }
        }

        return dropNames;
    }

    /**
     * Форматирует дропы в блоки.
     *
     * @param dropNames Список названий дропов.
     * @param blockSize Количество дропов в одном блоке.
     * @return Отформатированная строка с дропами.
     */
    private String formatDropsIntoBlocks(List<String> dropNames, int blockSize) {
        StringBuilder formattedDrops = new StringBuilder();
        int count = 0;

        for (String drop : dropNames) {
            formattedDrops.append("• ").append(drop).append("\n");
            count++;

            // Добавляем разделитель после каждого блока
            if (count % blockSize == 0) {
                formattedDrops.append("```\n```\n"); // Разделитель между блоками
            }
        }

        // Если дропы не делятся на блоки ровно, добавляем код-блок в конце
        if (count % blockSize != 0) {
            formattedDrops.append("```");
        }

        return formattedDrops.toString();
    }
}
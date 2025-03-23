package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ru.absolute.bot.dao.BossDao;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;
import ru.absolute.bot.utils.TimeUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
public class ShowCommand {
    private final BossService bossService;

    public ShowCommand(BossService bossService) {
        this.bossService = bossService;
    }

    public void handle(SlashCommandInteractionEvent event) {
        try {
            // Получаем всех боссов через BossService
            List<Boss> bosses = bossService.getAllBosses();

            // Фильтруем и сортируем боссов
            List<Boss> filteredBosses = filterAndSortBosses(bosses);

            // Разделяем боссов на группы
            List<Boss> recentlyEnded = new ArrayList<>();
            List<Boss> inRespawn = new ArrayList<>();
            List<Boss> upcoming = new ArrayList<>();
            categorizeBosses(filteredBosses, recentlyEnded, inRespawn, upcoming);

            // Формируем и отправляем сообщение
            MessageCreateBuilder messageBuilder = buildMessage(recentlyEnded, inRespawn, upcoming);
            event.reply(messageBuilder.build()).queue();
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /show", e);
            event.reply("Произошла ошибка при получении списка боссов.").setEphemeral(true).queue();
        }
    }


    /**
     * Фильтрует и сортирует боссов.
     */
    private List<Boss> filterAndSortBosses(List<Boss> bosses) {
        return bosses.stream()
                .filter(boss -> boss.getKillTime() != null) // Фильтруем боссов с пустым killTime
                .sorted(Comparator.comparing(TimeUtils::calculateRespawnWindowStart)) // Сортируем по времени респа
                .collect(Collectors.toList());
    }

    /**
     * Разделяет боссов на группы: недавно закончившиеся, в респе и ближайшие.
     */
    private void categorizeBosses(List<Boss> bosses, List<Boss> recentlyEnded, List<Boss> inRespawn, List<Boss> upcoming) {
        LocalDateTime now = LocalDateTime.now();
        for (Boss boss : bosses) {
            LocalDateTime respawnStart = TimeUtils.calculateRespawnWindowStart(boss);
            LocalDateTime respawnEnd = TimeUtils.calculateRespawnWindowEnd(boss);

            if (respawnStart == null || respawnEnd == null) {
                continue; // Пропускаем боссов с некорректным временем
            }

            if (now.isAfter(respawnStart) && now.isBefore(respawnEnd)) {
                inRespawn.add(boss); // Боссы в респе
            } else if (now.isAfter(respawnEnd) && ChronoUnit.HOURS.between(respawnEnd, now) < 1) {
                recentlyEnded.add(boss); // Боссы, у которых респ закончился менее часа назад
            } else if (now.isBefore(respawnStart)) {
                upcoming.add(boss); // Боссы, у которых респ еще не начался
            }
        }
    }

    /**
     * Формирует сообщение для отправки в Discord.
     */
    private MessageCreateBuilder buildMessage(List<Boss> recentlyEnded, List<Boss> inRespawn, List<Boss> upcoming) {
        MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
        messageBuilder.addContent("Время до респа боссов:\n");

        // Боссы, у которых респ закончился менее часа назад
        if (!recentlyEnded.isEmpty()) {
            messageBuilder.addContent("====== РЕСП ЗАКОНЧИЛСЯ ======\n");
            recentlyEnded.forEach(boss -> messageBuilder.addContent(formatRecentlyEndedBoss(boss)));
        }

        // Боссы в респе
        if (!inRespawn.isEmpty()) {
            messageBuilder.addContent("\n====== В РЕСПЕ ======\n");
            inRespawn.forEach(boss -> messageBuilder.addContent(formatInRespawnBoss(boss)));
        }

        // Ближайшие боссы
        if (!upcoming.isEmpty()) {
            messageBuilder.addContent("\n===== БЛИЖАЙШИЕ =====\n");
            upcoming.forEach(boss -> messageBuilder.addContent(formatUpcomingBoss(boss)));
        }

        // Если нет активных боссов
        if (inRespawn.isEmpty() && upcoming.isEmpty()) {
            messageBuilder.addContent("Нет активных боссов.");
        }

        return messageBuilder;
    }

    /**
     * Форматирует информацию о боссе, у которого респ закончился менее часа назад.
     */
    private String formatRecentlyEndedBoss(Boss boss) {
        long minutesSinceRespawnEnd = ChronoUnit.MINUTES.between(TimeUtils.calculateRespawnWindowEnd(boss), LocalDateTime.now());
        return String.format("%s (Ур. %d) - Респ закончился %d минут назад\n", boss.getName(), boss.getLevel(), minutesSinceRespawnEnd);
    }

    /**
     * Форматирует информацию о боссе в респе.
     */
    private String formatInRespawnBoss(Boss boss) {
        LocalDateTime now = LocalDateTime.now();
        long hoursUntilRespawnEnd = ChronoUnit.HOURS.between(now, TimeUtils.calculateRespawnWindowEnd(boss));
        long minutesUntilRespawnEnd = ChronoUnit.MINUTES.between(now, TimeUtils.calculateRespawnWindowEnd(boss)) % 60;
        return String.format("%s (Ур. %d) - До конца респа: %d ч. %d мин.\n", boss.getName(), boss.getLevel(), hoursUntilRespawnEnd, minutesUntilRespawnEnd);
    }

    /**
     * Форматирует информацию о ближайшем боссе.
     */
    private String formatUpcomingBoss(Boss boss) {
        LocalDateTime now = LocalDateTime.now();
        long hoursUntilRespawnStart = ChronoUnit.HOURS.between(now, TimeUtils.calculateRespawnWindowStart(boss));
        long minutesUntilRespawnStart = ChronoUnit.MINUTES.between(now, TimeUtils.calculateRespawnWindowStart(boss)) % 60;
        long secondsUntilRespawnStart = ChronoUnit.SECONDS.between(now, TimeUtils.calculateRespawnWindowStart(boss)) % 60;
        return String.format("%s (Ур. %d) - через %02d:%02d:%02d\n", boss.getName(), boss.getLevel(), hoursUntilRespawnStart, minutesUntilRespawnStart, secondsUntilRespawnStart);
    }
}
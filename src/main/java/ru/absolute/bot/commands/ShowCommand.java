package ru.absolute.bot.commands;

import lombok.SneakyThrows;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.utils.TimeUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ShowCommand {
    private final BossService bossService;

    @SneakyThrows
    public ShowCommand() {
        this.bossService = new BossService();
    }

    @SneakyThrows
    public void handle(SlashCommandInteractionEvent event) {
        try {
            // Получаем всех боссов
            List<Boss> bosses = bossService.getAllBosses();

            // Фильтруем боссов с пустым killTime
            bosses = bosses.stream()
                    .filter(boss -> boss.getKillTime() != null)
                    .collect(Collectors.toList());

            // Сортируем боссов по времени респа
            bosses.sort(Comparator.comparing(boss -> TimeUtils.calculateRespawnWindowStart(boss)));

            // Разделяем боссов на две группы
            List<Boss> recentlyEnded = new ArrayList<>();
            List<Boss> inRespawn = new ArrayList<>();
            List<Boss> upcoming = new ArrayList<>();

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

            // Формируем сообщение
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
            messageBuilder.addContent("Время до респа боссов:\n");

            // Боссы, у которых респ закончился менее часа назад
            if (!recentlyEnded.isEmpty()) {
                messageBuilder.addContent("====== РЕСП ЗАКОНЧИЛСЯ ======\n");
                for (Boss boss : recentlyEnded) {
                    long minutesSinceRespawnEnd = ChronoUnit.MINUTES.between(TimeUtils.calculateRespawnWindowEnd(boss), now);
                    String bossInfo = String.format("%s (Ур. %d) - Респ закончился %d минут назад\n", boss.getName(), boss.getLevel(), minutesSinceRespawnEnd);
                    messageBuilder.addContent(bossInfo);
                }
            }

            // Боссы в респе
            if (!inRespawn.isEmpty()) {
                messageBuilder.addContent("====== В РЕСПЕ ======\n");
                for (Boss boss : inRespawn) {
                    long hoursUntilRespawnEnd = ChronoUnit.HOURS.between(now, TimeUtils.calculateRespawnWindowEnd(boss));
                    long minutesUntilRespawnEnd = ChronoUnit.MINUTES.between(now, TimeUtils.calculateRespawnWindowEnd(boss)) % 60;
                    String bossInfo = String.format("%s (Ур. %d) - До конца респа: %d ч. %d мин.\n", boss.getName(), boss.getLevel(),hoursUntilRespawnEnd, minutesUntilRespawnEnd);
                    messageBuilder.addContent(bossInfo);
                }
            }

            // Ближайшие боссы
            if (!upcoming.isEmpty()) {
                messageBuilder.addContent("===== БЛИЖАЙШИЕ =====\n");
                for (Boss boss : upcoming) {
                    long hoursUntilRespawnStart = ChronoUnit.HOURS.between(now, TimeUtils.calculateRespawnWindowStart(boss));
                    long minutesUntilRespawnStart = ChronoUnit.MINUTES.between(now, TimeUtils.calculateRespawnWindowStart(boss)) % 60;
                    long secondsUntilRespawnStart = ChronoUnit.SECONDS.between(now, TimeUtils.calculateRespawnWindowStart(boss)) % 60;
                    String bossInfo = String.format("%s (Ур. %d) - через %02d:%02d:%02d\n", boss.getName(), boss.getLevel(), hoursUntilRespawnStart, minutesUntilRespawnStart, secondsUntilRespawnStart);
                    messageBuilder.addContent(bossInfo);
                }
            }

            // Отправляем сообщение
            if (inRespawn.isEmpty() && upcoming.isEmpty()) {
                messageBuilder.addContent("Нет активных боссов.");
            }

            event.reply(messageBuilder.build()).queue();
        } catch (Exception e) {
            event.reply("Произошла ошибка при получении списка боссов.").setEphemeral(true).queue();
        }
    }
}
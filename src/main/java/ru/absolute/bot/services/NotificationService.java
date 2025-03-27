package ru.absolute.bot.services;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ru.absolute.bot.commands.ShowCommand;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.utils.ConfigLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class NotificationService {
    @Getter
    private final long scheduleChannelId;
    private final long alertChannelId;
    private final BossService bossService;


    public NotificationService(BossService bossService) {
        this.scheduleChannelId = Long.parseLong(ConfigLoader.getProperty("bot.schedule_channel_id"));
        this.alertChannelId = Long.parseLong(ConfigLoader.getProperty("bot.alert_channel_id"));
        log.info("NotificationService initialized. Schedule channel: {}, Alert channel: {}",
                scheduleChannelId, alertChannelId);
        this.bossService = bossService;

    }

    public void sendRespawnAlert(JDA jda, Boss boss) {
        TextChannel channel = jda.getTextChannelById(alertChannelId);
        if (channel == null) {
            log.warn("Alert channel not found! ID: {}", alertChannelId);
            return;
        }

        // Получаем логины проверяющих
        List<String> checkerLogins = bossService.getCheckerLoginsForBoss(boss.getId());

        // Формируем компактное сообщение
        String message = String.format("@here Начало респа **%s** [Ур. %d] (%s)%s",
                boss.getName(),
                boss.getLevel(),
                boss.getLocation(),
                checkerLogins.isEmpty() ? "" : "\nПалилки: [" + String.join(", ", checkerLogins) + "]"
        );

        channel.sendMessage(message).queue(
                success -> log.info("Respawn alert sent for {}", boss.getName()),
                error -> log.error("Failed to send alert", error)
        );
    }

    public void cleanAndPostSchedule(JDA jda, ShowCommand showCommand) {
        TextChannel channel = jda.getTextChannelById(scheduleChannelId);
        if (channel == null) {
            log.warn("Schedule channel not found! ID: {}", scheduleChannelId);
            return;
        }

        log.info("Cleaning schedule channel {} and posting new schedule", channel.getName());
        channel.getIterableHistory().takeAsync(100).thenAccept(messages -> {
            // Фильтруем только сообщения бота
            List<CompletableFuture<Void>> deletions = messages.stream()
                    .filter(msg -> msg.getAuthor().equals(jda.getSelfUser()))
                    .map(msg -> msg.delete().submit()
                            .thenAccept(success ->
                                    log.debug("Deleted old schedule message {}", msg.getId()))
                            .exceptionally(e -> {
                                log.error("Failed to delete message {}", msg.getId(), e);
                                return null;
                            })
                    )
                    .collect(Collectors.toList());

            // После удаления публикуем новое расписание
            CompletableFuture.allOf(deletions.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        log.info("Channel cleaned, posting new schedule");
                        showCommand.postSchedule(channel);
                    })
                    .exceptionally(e -> {
                        log.error("Error during channel cleanup", e);
                        return null;
                    });
        }).exceptionally(e -> {
            log.error("Failed to retrieve channel history", e);
            return null;
        });
    }
}

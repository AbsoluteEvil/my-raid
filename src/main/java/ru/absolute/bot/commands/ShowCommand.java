package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.NotificationService;
import ru.absolute.bot.utils.TimeUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


@Slf4j
public class ShowCommand {
    private final BossService bossService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<Long, Message> activeMessages = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> notifiedBosses = new ConcurrentHashMap<>();
    private final NotificationService notificationService;
    private final long scheduleChannelId;

    public void handle(SlashCommandInteractionEvent event) {
        try {
            MessageCreateBuilder messageBuilder = createBossStatusMessage();
            event.reply(messageBuilder.build()).queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(message -> {
                    activeMessages.put(message.getIdLong(), message);
                    scheduler.scheduleAtFixedRate(() -> updateMessage(message), 1, 1, TimeUnit.MINUTES);
                });
            });
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /show", e);
            event.reply("Произошла ошибка при получении списка боссов.").setEphemeral(true).queue();
        }
    }

    public ShowCommand(BossService bossService, JDA jda) {
        this.bossService = bossService;
        this.notificationService = new NotificationService(bossService);
        this.scheduleChannelId = notificationService.getScheduleChannelId();
        initializeSchedulePosting(jda);


        // Автоматическая публикация при создании команды
        TextChannel channel = jda.getTextChannelById(notificationService.getScheduleChannelId());
        if (channel != null) {
            // Удаляем все предыдущие сообщения бота
            channel.getIterableHistory().takeAsync(100).thenAccept(messages -> {
                messages.forEach(msg -> {
                    if (msg.getAuthor().equals(jda.getSelfUser())) {
                        msg.delete().queue();
                    }
                });
                // Публикуем новое расписание
                postSchedule(channel);
            });
        }
    }

    private void initializeSchedulePosting(JDA jda) {
        log.info("[ShowCommand] Начало инициализации автоматической публикации расписания...");

        TextChannel channel = jda.getTextChannelById(scheduleChannelId);
        if (channel == null) {
            log.error("[ShowCommand] Канал расписания не найден! ID: {}", scheduleChannelId);
            return;
        }

        log.info("[ShowCommand] Канал расписания найден: {} (ID: {})", channel.getName(), channel.getId());

        // Добавляем задержку перед очисткой
        scheduler.schedule(() -> {
            log.info("[ShowCommand] Начало очистки канала...");

            // Ограничиваем количество запросов к истории
            channel.getHistoryBefore(channel.getLatestMessageId(), 100).queue(history -> {
                List<Message> botMessages = history.getRetrievedHistory().stream()
                        .filter(msg -> msg.getAuthor().equals(jda.getSelfUser()))
                        .collect(Collectors.toList());

                log.info("[ShowCommand] Найдено {} сообщений бота для удаления", botMessages.size());

                if (botMessages.isEmpty()) {
                    log.info("[ShowCommand] Нет сообщений бота для удаления");
                    postSchedule(channel);
                    return;
                }

                // Удаляем все сообщения последовательно
                deleteMessagesSequentially(channel, botMessages, 0);
            }, error -> {
                log.error("[ShowCommand] Ошибка при получении истории канала: {}", error.getMessage());
                postSchedule(channel); // Публикуем расписание даже при ошибке
            });
        }, 2, TimeUnit.SECONDS);
    }

    private void deleteMessagesSequentially(TextChannel channel, List<Message> messages, int index) {
        if (index >= messages.size()) {
            log.info("[ShowCommand] Все сообщения удалены. Публикуем новое расписание...");
            postSchedule(channel);
            return;
        }

        Message msg = messages.get(index);
        msg.delete().queue(
                success -> {
                    log.debug("[ShowCommand] Сообщение {} удалено", msg.getId());
                    deleteMessagesSequentially(channel, messages, index + 1);
                },
                error -> {
                    log.error("[ShowCommand] Ошибка удаления сообщения {}: {}", msg.getId(), error.getMessage());
                    deleteMessagesSequentially(channel, messages, index + 1);
                }
        );
    }


    private void updateMessage(Message message) {
        try {
            cleanUpMessages(); // Очистка перед обновлением
            MessageCreateBuilder newMessage = createBossStatusMessage();
            MessageEditBuilder editBuilder = new MessageEditBuilder();
            editBuilder.applyCreateData(newMessage.build());
            message.editMessage(editBuilder.build()).queue();
        } catch (Exception e) {
            log.error("Ошибка при обновлении сообщения", e);
        }
    }

    private void cleanUpMessages() {
        activeMessages.entrySet().removeIf(entry -> {
            try {
                Message message = entry.getValue();
                if (message == null || !message.isFromGuild()) {
                    return true; // Удалить если сообщение null или не из гильдии
                }

                // Получаем канал как GuildMessageChannel
                MessageChannel channel = message.getChannel();
                if (!(channel instanceof GuildMessageChannel guildChannel)) {
                    return true; // Удалить если не текстовый/новостной канал гильдии
                }

                // Проверяем доступность сообщения
                guildChannel.retrieveMessageById(message.getIdLong()).complete();
                return false; // Не удалять если сообщение доступно

            } catch (Exception e) {
                return true; // Удалить при любой ошибке
            }
        });
    }

    private MessageCreateBuilder createBossStatusMessage() {
        List<Boss> bosses = bossService.getAllBosses();
        List<Boss> filteredBosses = filterAndSortBosses(bosses);

        List<Boss> recentlyEnded = new ArrayList<>();
        List<Boss> inRespawn = new ArrayList<>();
        List<Boss> upcoming = new ArrayList<>();

        categorizeBosses(filteredBosses, recentlyEnded, inRespawn, upcoming);
        checkForRespawnStart(activeMessages.values(), filteredBosses);

        return buildMessage(recentlyEnded, inRespawn, upcoming);
    }

    private void checkForRespawnStart(Collection<Message> messages, List<Boss> bosses) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkWindowStart = now.minusMinutes(5);

        for (Message message : messages) {
            Set<Long> alreadyNotified = notifiedBosses.computeIfAbsent(message.getIdLong(), k -> new HashSet<>());
            MessageChannel channel = message.getChannel();

            for (Boss boss : bosses) {
                LocalDateTime respawnStart = TimeUtils.calculateRespawnWindowStart(boss);

                if (shouldNotify(respawnStart, checkWindowStart, now, alreadyNotified, (long) boss.getId())) {
                    sendRespawnNotification(channel, boss);
                    alreadyNotified.add((long) boss.getId());
                }
            }
        }
    }

    private boolean shouldNotify(LocalDateTime respawnStart, LocalDateTime checkWindowStart,
                                 LocalDateTime now, Set<Long> alreadyNotified, Long bossId) {
        return respawnStart != null &&
                respawnStart.isAfter(checkWindowStart) &&
                respawnStart.isBefore(now) &&
                !alreadyNotified.contains(bossId);
    }

    private void sendRespawnNotification(MessageChannel channel, Boss boss) {
        notificationService.sendRespawnAlert(channel.getJDA(), boss);
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
    private MessageCreateBuilder buildMessage(List<Boss> recentlyEnded,
                                              List<Boss> inRespawn,
                                              List<Boss> upcoming) {
        MessageCreateBuilder builder = new MessageCreateBuilder();

        builder.addContent("**Таблица респа боссов**\n");

        if (!recentlyEnded.isEmpty()) {
            builder.addContent("Недавно закончились:\n").addEmbeds();
            builder.addContent("```ini\n" + formatBossTable(recentlyEnded, "закончился") + "```\n");
        }

        if (!inRespawn.isEmpty()) {
            builder.addContent("Сейчас в респауне:\n");
            builder.addContent("```ini\n" + formatBossTable(inRespawn, "осталось") + "```\n");
        }

        if (!upcoming.isEmpty()) {
            builder.addContent("Ближайшие боссы:\n");
            builder.addContent("```ini\n" + formatBossTable(upcoming, "через") + "```\n");
        }

        if (inRespawn.isEmpty() && upcoming.isEmpty() && recentlyEnded.isEmpty()) {
            builder.addContent("ℹ️ В настоящее время нет активных боссов.");
        }

        return builder;
    }

    private String formatBossTable(List<Boss> bosses, String timePrefix) {
        int maxIconLength = 2;
        int maxLevelLength = bosses.stream().mapToInt(b -> String.valueOf(b.getLevel()).length()).max().orElse(2);
        int maxNameLength = bosses.stream().mapToInt(b -> b.getName().length()).max().orElse(20);

        String formatStr = "%-" + (maxIconLength + 1) + "s"  // Иконка + пробел
                + "%-" + (maxLevelLength + 2) + "d" // Уровень
                + "%-" + (maxNameLength + 2) + "s"  // Имя
                + "%s%n";                           // Время

        StringBuilder sb = new StringBuilder();
        for (Boss boss : bosses) {
            String icon = getBossIcon(boss.getName());
            String timeInfo = TimeUtils.formatBossRespawnStatus(boss);

            sb.append(String.format(formatStr,
                    icon,
                    boss.getLevel(),
                    boss.getName(),
                    timeInfo));
        }
        return sb.toString();
    }

    private String getBossIcon(String bossName) {
        return switch (bossName.toLowerCase()) {
            case "core", "orfen" -> "🔺";
            case "kernon", "death lord hallate", "longhorn golkonda" -> "🔹";
            case "flame of splendor barakiel" -> "🔸";
            default -> "  ";
        };
    }

    public void postSchedule(TextChannel channel) {
        if (activeMessages.values().stream().anyMatch(msg ->
                msg.getChannel().getIdLong() == channel.getIdLong())) {
            log.info("[ShowCommand] Активное сообщение уже существует в канале. Пропускаем публикацию.");
            return;
        }
        try {
            log.info("[ShowCommand] Создание сообщения с расписанием...");
            MessageCreateBuilder messageBuilder = createBossStatusMessage();

            channel.sendMessage(messageBuilder.build()).queue(
                    message -> {
                        log.info("[ShowCommand] Расписание опубликовано (ID: {})", message.getId());
                        activeMessages.put(message.getIdLong(), message);
                        scheduler.scheduleAtFixedRate(() -> updateMessage(message), 1, 1, TimeUnit.MINUTES);
                    },
                    error -> {
                        log.error("[ShowCommand] Ошибка публикации расписания: {}", error.getMessage());
                        // Повторная попытка через 30 секунд
                        scheduler.schedule(() -> postSchedule(channel), 30, TimeUnit.SECONDS);
                    }
            );
        } catch (Exception e) {
            log.error("[ShowCommand] Критическая ошибка при создании расписания: {}", e.getMessage());
        }
    }

    public void shutdown() {
        log.info("Завершение работы ShowCommand...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Принудительное завершение планировщика...");
                scheduler.shutdownNow();
            }
            log.info("Планировщик успешно остановлен");
        } catch (InterruptedException e) {
            log.error("Прерывание при завершении планировщика", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        activeMessages.clear();
        notifiedBosses.clear();
    }
}
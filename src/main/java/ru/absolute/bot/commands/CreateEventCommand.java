package ru.absolute.bot.commands;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import ru.absolute.bot.handlers.ReplyHandler;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class CreateEventCommand extends BaseCommand {
    private static final long TARGET_GUILD_ID = 1040951510898593855L;
    private static final String DROP_SELECTION_PREFIX = "drop_selection:";
    private static final String SKIP_DROPS_PREFIX = "skip_drops:";
    private static final String CONFIRM_DROPS_PREFIX = "confirm_drops:";

    private final Map<String, List<String>> selectedDropsCache = new ConcurrentHashMap<>();

    public CreateEventCommand(EventService eventService, BossService bossService) {
        super(bossService, eventService);
    }

    /**
     * Обрабатывает команду /create_event.
     */
    public void handle(SlashCommandInteractionEvent event) {
        handleEvent(event.getOption("boss_name").getAsString(), createReplyHandler(event));
    }

    public void handleButtonEvent(ButtonInteractionEvent event, String bossName) {
        handleEvent(bossName, createEditHandler(event));
        if (event.getComponentId().startsWith("skip_drops:")) {
            // Логика пропуска выбора дропов
            event.reply("Создаю событие без дропов для " + bossName).queue();
        }
        else if (event.getComponentId().startsWith("confirm_drops:")) {
            // Логика подтверждения дропов
            event.reply("Создаю событие с выбранными дропами для " + bossName).queue();
        }
    }

    // Обработчики взаимодействий
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        try {
            if (event.getComponentId().startsWith(DROP_SELECTION_PREFIX)) {
                String bossName = event.getComponentId().split(":")[1];
                String drops = String.join(",", event.getValues());

                logSelection(event.getUser().getId(), bossName, event.getValues());

                event.editMessage("Выбор сохранен. Нажмите 'Подтвердить', чтобы завершить.")
                        .setActionRow(Button.success(CONFIRM_DROPS_PREFIX + bossName + ":" + drops, "Подтвердить"))
                        .queue();
            }
        } catch (Exception e) {
            handleError(event, "Ошибка при обработке выбора дропа", e);
        }
    }

    public void handleSkipButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith(SKIP_DROPS_PREFIX)) {
            String bossName = event.getComponentId().split(":")[1];
            logAction(event.getUser().getId(), "нажал 'Пропустить' для босса:", bossName);

            createAndConfirmEvent(event, bossName, "");
        }
    }

    public void handleConfirmButtonInteraction(ButtonInteractionEvent event) {
        try {
            String[] parts = event.getComponentId().split(":");
            String bossName = parts[1];
            String drops = parts.length > 2 ? parts[2] : "";

            logAction(event.getUser().getId(), "нажал 'Подтвердить' для босса:", bossName);
            log.info("Выбранные дропы: {}", drops);

            createAndConfirmEvent(event, bossName, drops);
        } catch (Exception e) {
            handleError(event, "Ошибка при подтверждении выбора дропа", e);
        }
    }

    // Основная логика
    @SneakyThrows
    private void handleEvent(String bossName, ReplyHandler replyHandler) {
        Boss boss = bossService.findBossByName(bossName);
        List<String> itemIds = Optional.ofNullable(boss)
                .map(Boss::getItemList)
                .orElse(Collections.emptyList());

        StringSelectMenu dropMenu = createDropSelectMenu(itemIds, bossName);
        Button skipButton = Button.secondary(SKIP_DROPS_PREFIX + bossName, "Пропустить");
        Button confirmButton = Button.success(CONFIRM_DROPS_PREFIX + bossName, "Подтвердить");

        replyHandler.reply("Выберите дроп с босса " + bossName + " и нажмите 'Подтвердить':",
                dropMenu, skipButton, confirmButton);
    }

    private StringSelectMenu createDropSelectMenu(List<String> itemIds, String bossName) {
        Map<String, String> itemsMap = bossService.getItemsMap();

        List<String> uniqueItemIds = itemIds.stream()
                .distinct()
                .collect(Collectors.toList());

        logDropInfo(bossName, itemIds, uniqueItemIds);

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(DROP_SELECTION_PREFIX + bossName)
                .setPlaceholder("Выберите дроп")
                .setMinValues(1)
                .setMaxValues(Math.min(25, uniqueItemIds.size()));

        uniqueItemIds.forEach(itemId -> {
            String itemName = itemsMap.get(itemId);
            if (itemName != null) {
                menuBuilder.addOption(itemName, itemId);
            } else {
                log.warn("Не найдено название для дропа с ID: {}", itemId);
            }
        });

        return menuBuilder.build();
    }

    // Вспомогательные методы
    private void createAndConfirmEvent(ButtonInteractionEvent event, String bossName, String drops) {
        List<String> memberIds = getMembersFromVoiceChannel(event);
        String eventId = eventService.createEvent(bossName, drops, memberIds);

        String message = buildEventMessage(eventId, bossName, drops, memberIds, Objects.requireNonNull(event.getGuild()));
        event.editMessage(message).setComponents().queue();
    }

    private String buildEventMessage(String eventId, String bossName, String drops, List<String> memberIds, Guild guild) {
        String base = "Событие было создано.\nID: [%s]\nБосс: [%s]\n";
        String dropsPart = drops.isEmpty() ? "Дроп не выбран.\n" : "Дроп: [%s].\n";
        String membersPart = "\nУчастники:\n" + formatMembersByGroups(memberIds, guild);
        return String.format(base, eventId, bossName) +
                (drops.isEmpty() ? dropsPart : String.format(dropsPart, getDropNames(drops))) +
                membersPart;
    }

    private List<String> getMembersFromVoiceChannel(GenericInteractionCreateEvent event) {
        if (event == null || event.getMember() == null) {
            log.error("Событие или участник не найдены!");
            return Collections.emptyList();
        }

        return Optional.ofNullable(event.getJDA().getGuildById(TARGET_GUILD_ID))
                .map(guild -> guild.getMemberById(event.getMember().getId()))
                .map(Member::getVoiceState)
                .map(GuildVoiceState::getChannel)
                .map(channel -> channel.getMembers().stream()
                        .filter(m -> !m.getUser().isBot())
                        .map(ISnowflake::getId)
                        .collect(Collectors.toList()))
                .orElseGet(() -> {
                    log.error("Не удалось получить участников голосового канала");
                    return Collections.emptyList();
                });
    }

    // Обработчики автозаполнения
    public void handleAutocomplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery option = event.getFocusedOption();

        if ("boss_name".equals(option.getName())) {
            handleBossNameAutocomplete(event, option.getValue().toLowerCase());
        } else if ("drop".equals(option.getName())) {
            handleDropAutocomplete(event);
        }
    }

    private void handleBossNameAutocomplete(CommandAutoCompleteInteractionEvent event, String userInput) {
        try {
            List<Command.Choice> choices = bossService.findBossesByName(userInput).stream()
                    .map(boss -> new Command.Choice(boss.getName(), boss.getName()))
                    .limit(25)
                    .collect(Collectors.toList());

            event.replyChoices(choices).queue();
        } catch (Exception e) {
            log.error("Ошибка автозаполнения boss_name", e);
            event.replyChoices().queue();
        }
    }

    private void handleDropAutocomplete(CommandAutoCompleteInteractionEvent event) {
        Optional.ofNullable(event.getOption("boss_name"))
                .map(OptionMapping::getAsString)
                .map(bossService::findBossByName)
                .map(Boss::getItemList)
                .ifPresentOrElse(
                        itemIds -> sendDropChoices(event, itemIds),
                        () -> event.replyChoices().queue()
                );
    }

    private void sendDropChoices(CommandAutoCompleteInteractionEvent event, List<String> itemIds) {
        Map<String, String> itemsMap = bossService.getItemsMap();
        List<Command.Choice> choices = itemIds.stream()
                .map(itemId -> new Command.Choice(itemsMap.get(itemId), itemId))
                .limit(25)
                .collect(Collectors.toList());

        event.replyChoices(choices).queue();
    }

    // Вспомогательные методы для логирования
    private void logDropInfo(String bossName, List<String> itemIds, List<String> uniqueItemIds) {
        log.info("Дроп с босса {}: {}", bossName, itemIds);
        log.info("Уникальные дроп с босса {}: {}", bossName, uniqueItemIds);
    }

    private void logSelection(String userId, String bossName, List<String> selectedDrops) {
        log.info("Пользователь {} выбрал дроп с босса {}: {}", userId, bossName, selectedDrops);
    }

    private void logAction(String userId, String action, String bossName) {
        log.info("Пользователь {} {} {}", userId, action, bossName);
    }

    // Фабричные методы для обработчиков ответов
    private ReplyHandler createReplyHandler(SlashCommandInteractionEvent event) {
        return (message, dropMenu, skipButton, confirmButton) -> {
            if (dropMenu != null && skipButton != null && confirmButton != null) {
                event.reply(message)
                        .addActionRow(dropMenu)
                        .addActionRow(skipButton, confirmButton)
                        .setEphemeral(true)
                        .queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        };
    }

    private ReplyHandler createEditHandler(ButtonInteractionEvent event) {
        return (message, dropMenu, skipButton, confirmButton) -> {
            if (dropMenu != null && skipButton != null && confirmButton != null) {
                event.editMessage(message)
                        .setComponents(
                                ActionRow.of(dropMenu),
                                ActionRow.of(skipButton, confirmButton)
                        )
                        .queue();
            } else {
                event.editMessage(message).queue();
            }
        };
    }
}
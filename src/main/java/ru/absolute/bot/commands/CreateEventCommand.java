package ru.absolute.bot.commands;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import ru.absolute.bot.handlers.ReplyHandler;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class CreateEventCommand {
    private final EventService eventService;
    private final BossService bossService;
    private final ConcurrentHashMap<String, List<String>> selectedDropsCache = new ConcurrentHashMap<>();

    public CreateEventCommand(EventService eventService, BossService bossService) {
        this.eventService = eventService;
        this.bossService = bossService;
    }

    /**
     * Обрабатывает событие создания ивента.
     */
    @SneakyThrows
    private void handleEvent(String bossName, ReplyHandler replyHandler) {
        Boss boss = bossService.findBossByName(bossName);
        // Получаем список ID дропов из кэша боссов
        List<String> itemIds = boss.getItemList();

        // Получаем карту предметов из кэша
        Map<String, String> itemsMap = bossService.getItemsMap();

        // Создаем список названий предметов
        List<String> itemNames = new ArrayList<>();
        for (String itemId : itemIds) {
            String itemName = itemsMap.get(itemId);
            if (itemName != null) {
                itemNames.add(itemName);
            } else {
                log.warn("Не найдено название для дропа с ID: {}", itemId);
            }
        }

            StringSelectMenu dropMenu = createDropSelectMenu(itemIds, bossName);
            Button skipButton = Button.secondary("skip_drops:" + bossName, "Пропустить");
            Button confirmButton = Button.success("confirm_drops:" + bossName, "Подтвердить");

            replyHandler.reply("Выберите дроп с босса " + bossName + " и нажмите 'Подтвердить':", dropMenu, skipButton, confirmButton);

    }

    /**
     * Обрабатывает команду /create_event.
     */
    public void handle(SlashCommandInteractionEvent event) {
        String bossName = event.getOption("boss_name").getAsString();
        handleEvent(bossName, (message, dropMenu, skipButton, confirmButton) -> {
            if (dropMenu != null && skipButton != null && confirmButton != null) {
                event.reply(message)
                        .addActionRow(dropMenu)
                        .addActionRow(skipButton, confirmButton)
                        .setEphemeral(true)
                        .queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        });
    }

    /**
     * Обрабатывает нажатие кнопки.
     */
    public void handleButtonEvent(ButtonInteractionEvent event, String bossName) {
        handleEvent(bossName, (message, dropMenu, skipButton, confirmButton) -> {
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
        });
    }

    /**
     * Создает меню выбора дропов.
     */
    private StringSelectMenu createDropSelectMenu(List<String> itemIds, String bossName) {
        Map<String, String> itemsMap = bossService.getItemsMap();
        log.info("Дропы для босса {}: {}", bossName, itemIds);

        List<String> uniqueItemIds = itemIds.stream()
                .distinct()
                .collect(Collectors.toList());

        log.info("Уникальные дропы для босса {}: {}", bossName, uniqueItemIds);

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("drop_selection:" + bossName)
                .setPlaceholder("Выберите дроп")
                .setMinValues(1)
                .setMaxValues(Math.min(25, uniqueItemIds.size()));

        for (String itemId : uniqueItemIds) {
            String itemName = itemsMap.get(itemId);
            if (itemName != null) {
                menuBuilder.addOption(itemName, itemId);
            } else {
                log.warn("Не найдено название для дропа с ID: {}", itemId);
            }
        }

        return menuBuilder.build();
    }

    /**
     * Обрабатывает выбор дропов в меню.
     */
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        log.info(event.getComponentId());
        try {
            if (event.getComponentId().startsWith("drop_selection:")) {
                String bossName = event.getComponentId().split(":")[1];
                List<String> selectedDrops = event.getValues();

                log.info("Пользователь {} выбрал дропы для босса {}: {}", event.getUser().getId(), bossName, selectedDrops);

                String drops = String.join(",", selectedDrops);
                Button confirmButton = Button.success("confirm_drops:" + bossName + ":" + drops, "Подтвердить");

                event.editMessage("Выбор сохранен. Нажмите 'Подтвердить', чтобы завершить.")
                        .setActionRow(confirmButton)
                        .queue();
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке выбора дропа", e);
            event.reply("Произошла ошибка при обработке выбора дропа.").setEphemeral(true).queue();
        }
    }

    /**
     * Обрабатывает нажатие кнопки "Пропустить".
     */
    public void handleSkipButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith("skip_drops:")) {
            String bossName = event.getComponentId().split(":")[1];
            log.info("Пользователь {} нажал 'Пропустить' для босса: {}", event.getUser().getId(), bossName);

            List<String> members = getMembersFromVoiceChannel(event);
            String eventId = eventService.createEvent(bossName, "", members);

            event.editMessage("Дроп не выбран.\nСобытие создано. ID: " + eventId)
                    .setComponents()
                    .queue();
        }
    }

    /**
     * Обрабатывает нажатие кнопки "Подтвердить".
     */
    public void handleConfirmButtonInteraction(ButtonInteractionEvent event) {
        try {
            String[] parts = event.getComponentId().split(":");
            String bossName = parts[1];
            String drops = parts.length > 2 ? parts[2] : "";

            log.info("Пользователь {} нажал 'Подтвердить' для босса: {}", event.getUser().getId(), bossName);
            log.info("Выбранные дропы: {}", drops);

            List<String> members = getMembersFromVoiceChannel(event);
            String eventId = eventService.createEvent(bossName, drops, members);

            String message = drops.isEmpty()
                    ? "Событие было создано.\nID: [" + eventId + "]\nБосс: [" + bossName + "]\nДроп не выбран.\nУчастники: " + members.toString()
                    : "Событие было создано.\nID: [" + eventId + "]\nБосс: [" + bossName + "]\nДроп: [" + getDropNames(drops) + "].\nУчастники: " + members.toString();

            event.editMessage(message)
                    .setComponents()
                    .queue();
        } catch (Exception e) {
            log.error("Ошибка при подтверждении выбора дропа", e);
            event.reply("Произошла ошибка при подтверждении выбора дропа.").setEphemeral(true).queue();
        }
    }

    /**
     * Получает список участников из голосового канала.
     */
    private List<String> getMembersFromVoiceChannel(ButtonInteractionEvent event) {
        List<String> members = new ArrayList<>();
        if (event.getMember() != null && event.getMember().getVoiceState() != null) {
            if (event.getMember().getVoiceState().getChannel() != null) {
                members = event.getMember().getVoiceState().getChannel().getMembers().stream()
                        .map(Member::getEffectiveName)
                        .toList();
            }
        }
        return members;
    }

    /**
     * Получает названия дропов по их ID.
     */
    private String getDropNames(String drops) {
        Map<String, String> itemsMap = bossService.getItemsMap();
        return Arrays.stream(drops.split(","))
                .map(itemsMap::get)
                .collect(Collectors.joining(", "));
    }

    /**
     * Обрабатывает автозаполнение для команды /create_event.
     */
    @SneakyThrows
    public void handleAutocomplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery option = event.getFocusedOption();
        if (option.getName().equals("boss_name")) {
            String userInput = option.getValue().toLowerCase();
            try {
                List<Command.Choice> choices = bossService.findBossesByName(userInput).stream()
                        .map(boss -> new Command.Choice(boss.getName(), boss.getName()))
                        .limit(25)
                        .collect(Collectors.toList());

                event.replyChoices(choices).queue();
            } catch (Exception e) {
                log.error("Ошибка при обработке автозаполнения для boss_name", e);
                event.replyChoices().queue();
            }
        } else if (option.getName().equals("drop")) {
            String bossName = event.getOption("boss_name").getAsString();
            Boss boss = bossService.findBossByName(bossName);
            if (boss != null) {
                List<String> itemIds = boss.getItemList();
                Map<String, String> itemsMap = bossService.getItemsMap();

                List<Command.Choice> choices = itemIds.stream()
                        .map(itemId -> new Command.Choice(itemsMap.get(itemId), itemId))
                        .limit(25)
                        .collect(Collectors.toList());

                event.replyChoices(choices).queue();
            } else {
                event.replyChoices().queue();
            }
        }
    }
}
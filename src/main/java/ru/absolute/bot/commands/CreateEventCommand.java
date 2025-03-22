package ru.absolute.bot.commands;

import lombok.SneakyThrows;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.handlers.ReplyHandler;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;
import ru.absolute.bot.services.GoogleSheetsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateEventCommand {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsService.class);
    private final EventService eventService;
    private final BossService bossService;
    private final GoogleSheetsService sheetsService;

    private final ConcurrentHashMap<String, List<String>> selectedDropsCache = new ConcurrentHashMap<>();

    @SneakyThrows
    public CreateEventCommand() {
        this.eventService = new EventService();
        this.bossService = new BossService();
        this.sheetsService = new GoogleSheetsService();
    }

    private void handleEvent(String bossName, ReplyHandler replyHandler) {
        Boss boss = bossService.findBossByName(bossName);

        if (boss != null) {
            // Получаем список ID вещей для босса
            List<String> itemIds = boss.getItemList();

            // Создаем StringSelectMenu на основе списка ID с именем босса в ID
            StringSelectMenu dropMenu = createDropSelectMenu(itemIds, bossName);

            // Создаем кнопку "Пропустить" с именем босса в ID
            Button skipButton = Button.secondary("skip_drops:" + bossName, "Пропустить");

            // Создаем кнопку "Подтвердить" с именем босса в ID
            Button confirmButton = Button.success("confirm_drops:" + bossName, "Подтвердить");

            // Используем ReplyHandler для отправки сообщения
            replyHandler.reply("Выберите дроп с босса " + bossName + " и нажмите 'Подтвердить':", dropMenu, skipButton, confirmButton);
        } else {
            // Если босс не найден, отправляем сообщение об ошибке
            replyHandler.reply("Босс с именем " + bossName + " не найден.", null, null, null);
        }
    }

    // Метод для обработки SlashCommandInteractionEvent
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


    public void handleButtonEvent(ButtonInteractionEvent event, String bossName) {
        handleEvent(bossName, (message, dropMenu, skipButton, confirmButton) -> {
            if (dropMenu != null && skipButton != null && confirmButton != null) {
                event.editMessage(message)
                        .setComponents(
                                ActionRow.of(dropMenu), // StringSelectMenu в отдельной строке
                                ActionRow.of(skipButton, confirmButton) // Кнопки в другой строке
                        )
                        .queue();
            } else {
                event.editMessage(message).queue();
            }
        });
    }

    private StringSelectMenu createDropSelectMenu(List<String> itemIds, String bossName) {
        Map<String, String> itemsMap = sheetsService.getItemsMap();
        logger.info("Дропы для босса {}: {}", bossName, itemIds);

        List<String> uniqueItemIds = itemIds.stream()
                .distinct()
                .collect(Collectors.toList());

        logger.info("Уникальные дропы для босса {}: {}", bossName, uniqueItemIds);

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("drop_selection:" + bossName)
                .setPlaceholder("Выберите дроп")
                .setMinValues(1)
                .setMaxValues(Math.min(25, uniqueItemIds.size()));

        for (String itemId : uniqueItemIds) {
            String itemName = itemsMap.get(itemId);
            if (itemName != null) {
                menuBuilder.addOption(itemName, itemId);
            } else {
                logger.warn("Не найдено название для дропа с ID: {}", itemId);
            }
        }

        StringSelectMenu menu = menuBuilder.build();
        logger.info("Созданное меню для босса {}: {}", bossName, menu);
        logger.info("Опции меню: {}", menu.getOptions());

        return menu;
    }

    public void handleSelectMenu(StringSelectInteractionEvent event) {
        logger.info(event.getComponentId());
        try {
            if (event.getComponentId().startsWith("drop_selection:")) {
                String bossName = event.getComponentId().split(":")[1];
                List<String> selectedDrops = event.getValues();

                logger.info("Пользователь {} выбрал дропы для босса {}: {}", event.getUser().getId(), bossName, selectedDrops);

                // Формируем строку с выбранными дропами
                String drops = String.join(",", selectedDrops);

                // Создаем кнопку "Подтвердить" с выбранными дропами в ID
                Button confirmButton = Button.success("confirm_drops:" + bossName + ":" + drops, "Подтвердить");

                // Обновляем сообщение, убираем меню выбора дропов и добавляем кнопку "Подтвердить"
                event.editMessage("Выбор сохранен. Нажмите 'Подтвердить', чтобы завершить.")
                        .setActionRow(confirmButton) // Добавляем кнопку "Подтвердить"
                        .queue();
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке выбора дропа", e);
            event.reply("Произошла ошибка при обработке выбора дропа.").setEphemeral(true).queue();
        }
    }


    public void handleSkipButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith("skip_drops:")) {
            String bossName = event.getComponentId().split(":")[1];
            logger.info("Пользователь {} нажал 'Пропустить' для босса: {}", event.getUser().getId(), bossName);

            // Получаем список участников
            List<String> members = getMembersFromVoiceChannel(event);

            // Создаем событие с пустыми дропами
            String eventId = eventService.createEvent(bossName, "", members);

            // Формируем сообщение для пользователя
            String message = "Дроп не выбран.\nСобытие создано. ID: " + eventId;

            // Обновляем сообщение, убираем кнопки
            event.editMessage(message)
                    .setComponents() // Убираем все компоненты (кнопки)
                    .queue();
        }
    }

    public void handleConfirmButtonInteraction(ButtonInteractionEvent event) {
        try {
            // Извлекаем имя босса и выбранные дропы из ID кнопки
            String[] parts = event.getComponentId().split(":");
            String bossName = parts[1];
            String drops = parts.length > 2 ? parts[2] : "";

            logger.info("Пользователь {} нажал 'Подтвердить' для босса: {}", event.getUser().getId(), bossName);
            logger.info("Выбранные дропы: {}", drops);

            // Получаем список участников
            List<String> members = getMembersFromVoiceChannel(event);

            // Создаем событие
            String eventId = eventService.createEvent(bossName, drops, members);

            // Формируем сообщение для пользователя
            String message;
            if (drops.isEmpty()) {
                message = "Событие было создано.\nID: [\" + eventId +\"] \\nБосс: [\"+bossName+\"]\\nДроп не выбран.\nУчастники: "+members.toString();
            } else {
                String dropNames = getDropNames(drops);
                message = "Событие было создано.\nID: [" + eventId +"] \nБосс: ["+bossName+"]\nДроп: ["+ dropNames +"].\nУчастники: "+members.toString();
            }

            // Обновляем сообщение, убираем кнопки
            event.editMessage(message)
                    .setComponents() // Убираем все компоненты (кнопки)
                    .queue();

        } catch (Exception e) {
            logger.error("Ошибка при подтверждении выбора дропа", e);
            event.reply("Произошла ошибка при подтверждении выбора дропа.").setEphemeral(true).queue();
        }
    }

    private void handleEventCreation(ButtonInteractionEvent event, String bossName, List<String> selectedDrops) {
        try {
            // Получаем список участников
            List<String> members = getMembersFromVoiceChannel(event);

            // Формируем строку с дропами (пустая строка для "Пропустить")
            String drops = selectedDrops != null ? String.join(",", selectedDrops) : "";

            // Создаем событие
            String eventId = eventService.createEvent(bossName, drops, members);

            // Формируем сообщение для пользователя
            String message;
            if (drops.isEmpty()) {
                message = "Дроп не выбран.\nСобытие создано. ID: " + eventId;
            } else {
                String dropNames = getDropNames(drops);
                message = "Дроп с РБ: " + dropNames + "\nСобытие создано. ID: " + eventId;
            }

            // Обновляем сообщение, убираем меню и кнопки
            event.editMessage(message)
                    .setComponents() // Убираем все компоненты (меню и кнопки)
                    .queue();

        } catch (Exception e) {
            logger.error("Ошибка при создании события", e);
            event.reply("Произошла ошибка при создании события.").setEphemeral(true).queue();
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
     * Создает событие и отправляет сообщение с результатом.
     */
    private void createAndSendEvent(ButtonInteractionEvent event, String bossName, String drops, List<String> members) {
        try {
            // Логируем начало создания события
            logger.info("Создание события для босса: {}", bossName);
            logger.info("Дропы: {}", drops);
            logger.info("Участники: {}", members);

            // Создаем событие
            String eventId = eventService.createEvent(bossName, drops, members);

            // Логируем успешное создание события
            logger.info("Событие успешно создано с ID: {}", eventId);

            // Формируем сообщение
            String message = drops.isEmpty()
                    ? "Дроп не выбран.\nСобытие создано. ID: " + eventId
                    : "Дроп с РБ: " + getDropNames(drops) + "\nСобытие создано. ID: " + eventId;

            // Удаляем StringSelectMenu и отправляем финальное сообщение
            event.editMessage(message)
                    .setComponents() // Удаляем все компоненты (меню и кнопки)
                    .queue();

        } catch (Exception e) {
            logger.error("Ошибка при создании события", e);
            event.reply("Произошла ошибка при создании события.").setEphemeral(true).queue();
        }
    }

    /**
     * Получает названия дропов по их ID.
     */
    private String getDropNames(String drops) {
        Map<String, String> itemsMap = sheetsService.getItemsMap();
        return Arrays.stream(drops.split(","))
                .map(itemsMap::get)
                .collect(Collectors.joining(", "));
    }

    @SneakyThrows
    public void handleAutocomplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery option = event.getFocusedOption();
        if (option.getName().equals("boss_name")) {
            String userInput = option.getValue().toLowerCase();
            try {
                // Получаем список боссов, соответствующих вводу пользователя
                List<Command.Choice> choices = bossService.findBossesByName(userInput).stream()
                        .map(boss -> new Command.Choice(boss.getName(), boss.getName()))
                        .limit(25) // Ограничиваем количество вариантов до 25
                        .collect(Collectors.toList());

                // Отправляем варианты автозаполнения
                event.replyChoices(choices).queue();
            } catch (Exception e) {
                logger.error("Ошибка при обработке автозаполнения для boss_name", e);
                event.replyChoices().queue(); // Отправляем пустой список в случае ошибки
            }
        } else if (option.getName().equals("drop")) {
            // Обработка автозаполнения для дропов (если нужно)
            String bossName = event.getOption("boss_name").getAsString();
            Boss boss = bossService.findBossByName(bossName);
            if (boss != null) {
                // Получаем список ID вещей для босса
                List<String> itemIds = boss.getItemList();

                // Получаем только нужные строки из таблицы items
                List<List<Object>> filteredRows = sheetsService.getItemsByIds(itemIds);

                // Преобразуем строки в варианты для автозаполнения
                List<Command.Choice> choices = new ArrayList<>();
                for (List<Object> row : filteredRows) {
                    String itemId = row.get(0).toString();
                    String itemName = row.get(1).toString();
                    choices.add(new Command.Choice(itemName, itemId));
                }

                // Ограничиваем количество вариантов до 25
                if (choices.size() > 25) {
                    choices = choices.subList(0, 25);
                }

                event.replyChoices(choices).queue();
            } else {
                event.replyChoices().queue();
            }
        }
    }
}
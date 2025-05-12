package ru.absolute.bot.commands;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.services.EventService;
import ru.absolute.bot.services.BossService;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EditEventCommand extends BaseCommand {
    private static final String EDIT_MEMBERS_BUTTON = "edit_members";
    private static final String FINISH_EDIT_BUTTON = "finish_edit";
    private static final String MEMBERS_SELECTION_MENU = "select_members";
    private static final String CONFIRM_EDIT_BUTTON = "confirm_edit";
    private static final String CANCEL_EDIT_BUTTON = "cancel_edit";

    @Getter
    private final Map<String, EditingSession> editingSessions = new HashMap<>();

    public EditEventCommand(EventService eventService, BossService bossService) {
        super(bossService, eventService);
    }

    public void handleEditMembersCommand(SlashCommandInteractionEvent event) {
        try {
            if (event.getOption("id") == null) {
                event.reply("Не указан ID события").setEphemeral(true).queue();
                return;
            }

            String eventId = event.getOption("id").getAsString();
            String userId = event.getUser().getId();

            Event dbEvent = eventService.findEventById(eventId);
            if (dbEvent == null) {
                event.reply("Событие с ID " + eventId + " не найдено.").setEphemeral(true).queue();
                return;
            }

            // Безопасное получение списка участников
            List<String> members = dbEvent.getMembers() != null ?
                    new ArrayList<>(dbEvent.getMembers()) :
                    new ArrayList<>();

            editingSessions.put(userId, new EditingSession(eventId, members, event.getGuild()));
            showInitialMembersList(event, members);
        } catch (Exception e) {
            e.printStackTrace();
            event.reply("Ошибка при редактировании: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void showInitialMembersList(SlashCommandInteractionEvent event, List<String> members) {
        String formattedMembers = formatMembersByGroups(members, event.getGuild());

        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.primary(EDIT_MEMBERS_BUTTON, "Изменить список"));
        buttons.add(Button.success(FINISH_EDIT_BUTTON, "Завершить"));

        event.reply("**Текущие участники:**\n" + formattedMembers)
                .setComponents(ActionRow.of(buttons))
                .setEphemeral(true)
                .queue();
    }

    public void handleButtonInteraction(ButtonInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue(); // Немедленный ответ

        String userId = event.getUser().getId();
        EditingSession session = editingSessions.get(userId);

        if (session == null) {
            event.getHook().sendMessage("Сессия редактирования истекла. Начните заново.").queue();
            return;
        }

        switch (event.getComponentId()) {
            case EDIT_MEMBERS_BUTTON:
                showMemberSelectionMenu(event, session);
                break;
            case CONFIRM_EDIT_BUTTON:
                showMembersListWithControls(event, session.getCurrentMembers(), "Изменения сохранены.");
                break;
            case CANCEL_EDIT_BUTTON:
                session.revertChanges();
                showMembersListWithControls(event, session.getCurrentMembers(), "Изменения отменены.");
                break;
            case FINISH_EDIT_BUTTON:
                completeEditing(event);
                break;
        }
    }

    private void showMembersListWithControls(ButtonInteractionEvent event, List<String> members, String message) {
        String formattedMembers = formatMembersByGroups(members, event.getGuild());
        String fullMessage = (message != null ? message + "\n\n" : "") +
                "**Текущие участники:**\n" + formattedMembers;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.primary(EDIT_MEMBERS_BUTTON, "Изменить список"));
        buttons.add(Button.success(FINISH_EDIT_BUTTON, "Завершить"));

        event.getHook().editOriginal(fullMessage)
                .setComponents(ActionRow.of(buttons))
                .queue();
    }

    public void showMemberSelectionMenu(ButtonInteractionEvent event, EditingSession session) {
        // Создаем EntitySelectMenu
        EntitySelectMenu selectMenu = EntitySelectMenu.create(MEMBERS_SELECTION_MENU,
                        EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder("Выберите участников (текущие выбраны автоматически)")
                .setRequiredRange(0, 25)
                .build();

        // Получаем список текущих участников для отображения
        String currentMembersText = formatMembersByGroups(session.getCurrentMembers(), session.getGuild());

        // Отправляем сообщение с текущим списком
        event.editMessage("**Текущие участники:**\n" + currentMembersText +
                        "\n\nВыберите участников в меню ниже:")
                .setComponents(
                        ActionRow.of(selectMenu),
                        ActionRow.of(
                                Button.success(CONFIRM_EDIT_BUTTON, "Сохранить"),
                                Button.danger(CANCEL_EDIT_BUTTON, "Отменить")
                        )
                )
                .queue();
    }

    public void handleMemberSelection(EntitySelectInteractionEvent event) {
        String userId = event.getUser().getId();
        EditingSession session = editingSessions.get(userId);

        if (session == null) {
            event.reply("Сессия редактирования истекла. Начните заново.").setEphemeral(true).queue();
            return;
        }

        // Получаем выбранные ID
        List<String> selectedIds = event.getMentions().getMembers().stream()
                .map(Member::getId)
                .collect(Collectors.toList());

        session.saveState();
        session.setCurrentMembers(selectedIds);

        // Показываем обновленный список
        String updatedList = formatMembersByGroups(selectedIds, event.getGuild());
        event.editMessage("**Новый список участников:**\n" + updatedList +
                        "\n\nПодтвердите изменения:")
                .setComponents(
                        ActionRow.of(
                                Button.success(CONFIRM_EDIT_BUTTON, "Сохранить"),
                                Button.danger(CANCEL_EDIT_BUTTON, "Отменить")
                        )
                )
                .queue();
    }

    public void completeEditing(ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        EditingSession session = editingSessions.get(userId);

        if (session == null) {
            event.reply("Сессия редактирования истекла. Начните заново.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            Event dbEvent = eventService.findEventById(session.getEventId());
            if (dbEvent == null) {
                event.reply("Событие с ID " + session.getEventId() + " не найдено.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            List<String> originalMembers = dbEvent.getMembers();
            List<String> currentMembers = session.getCurrentMembers();

            // Обновляем событие в базе данных
            eventService.editEvent(
                    session.getEventId(),
                    null,
                    currentMembers.stream().filter(m -> !originalMembers.contains(m)).collect(Collectors.toList()),
                    originalMembers.stream().filter(m -> !currentMembers.contains(m)).collect(Collectors.toList())
            );

            // 1. Обновляем исходное сообщение (убираем кнопки)
            String updatedList = formatMembersByGroups(currentMembers, event.getGuild());
            event.editMessage("✅ Список участников обновлен!\n\n**Текущие участники:**\n" + updatedList)
                    .setComponents() // Убираем все кнопки
                    .queue();

            // 2. Отправляем публичное уведомление (если нужно)
            String publicMessage = String.format(
                    "Изменения в событии \"%s, %s [%s]\", сделанные пользователем %s сохранены.\n\nУчастники:\n%s",
                    dbEvent.getBossName(),
                    dbEvent.getDate(),
                    dbEvent.getId(),
                    event.getUser().getAsMention(),
                    updatedList
            );
            event.getChannel().sendMessage(publicMessage).queue();

            editingSessions.remove(userId);
        } catch (Exception e) {
            event.reply("Произошла ошибка при обновлении события: " + e.getMessage())
                    .setEphemeral(true)
                    .queue();
        }
    }

    public static class EditingSession {
        @Getter
        private final String eventId;
        @Getter
        private List<String> currentMembers;
        private List<String> previousState;
        @Getter
        private final Guild guild;

        public EditingSession(String eventId, List<String> initialMembers, Guild guild) {
            this.eventId = eventId;
            this.guild = guild;
            this.currentMembers = new ArrayList<>(initialMembers);
        }

        public void setCurrentMembers(List<String> members) {
            this.currentMembers = new ArrayList<>(members);
        }

        public void saveState() {
            previousState = new ArrayList<>(currentMembers);
        }

        public void revertChanges() {
            if (previousState != null) {
                currentMembers = new ArrayList<>(previousState);
            }
        }
    }

    public void handleAutocomplete(CommandAutoCompleteInteractionEvent event) {
        try {
            if ("id".equals(event.getFocusedOption().getName())) {
                handleEventIdAutocomplete(event);
            } else if ("status".equals(event.getFocusedOption().getName())) {
                handleStatusAutocomplete(event);
            }
        } catch (Exception e) {
            event.replyChoices(new ArrayList<>()).queue();
        }
    }

    private void handleEventIdAutocomplete(CommandAutoCompleteInteractionEvent event) throws IOException {
        List<Command.Choice> options = eventService.getEventsByStatus(EventStatus.IN_PROGRESS)
                .stream()
                .sorted((e1, e2) -> e2.getDate().compareTo(e1.getDate())) // Сортировка по убыванию даты
                .map(e -> new Command.Choice(e.getBossName() + " (" + e.getDate() + ")", e.getId()))
                .limit(25)
                .collect(Collectors.toList());
        event.replyChoices(options).queue();
    }

    private void handleStatusAutocomplete(CommandAutoCompleteInteractionEvent event) {
        List<Command.Choice> options = Arrays.stream(EventStatus.values())
                .map(s -> new Command.Choice(s.name(), s.name()))
                .collect(Collectors.toList());
        event.replyChoices(options).queue();
    }

    public Optional<EditingSession> getEditingSession(String userId) {
        return Optional.ofNullable(editingSessions.get(userId));
    }

}
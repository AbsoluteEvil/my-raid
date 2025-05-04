package ru.absolute.bot.handlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ru.absolute.bot.commands.CreateEventCommand;
import ru.absolute.bot.commands.EditEventCommand;

@Slf4j
public class ButtonHandler extends ListenerAdapter {
    private final CreateEventCommand createEventCommand;
    private final EditEventCommand editEventCommand;

    public ButtonHandler(CreateEventCommand createEventCommand, EditEventCommand editEventCommand) {
        this.createEventCommand = createEventCommand;
        this.editEventCommand = editEventCommand;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        try {
            String buttonId = event.getComponentId();
            switch (buttonId) {
                case String id when id.startsWith("skip_drops:") ->
                        createEventCommand.handleSkipButtonInteraction(event);
                case String id when id.startsWith("confirm_drops:") ->
                        createEventCommand.handleConfirmButtonInteraction(event);
                case String id when id.startsWith("ok_") ->
                        handleOkButton(event, buttonId);
                case String id when id.startsWith("create_event_") ->
                        handleCreateEventButton(event, buttonId);
                case "edit_members" -> {
                    String userId = event.getUser().getId();
                    editEventCommand.getEditingSession(userId).ifPresentOrElse(
                            session -> editEventCommand.showMemberSelectionMenu(event, session),
                            () -> event.reply("Сессия редактирования не найдена. Начните заново.")
                                    .setEphemeral(true).queue()
                    );
                }
                case "confirm_edit", "cancel_edit", "finish_edit" ->
                        editEventCommand.handleButtonInteraction(event);
                default -> {
                    log.warn("Неизвестная кнопка: {}", buttonId);
                    event.reply("Неизвестная команда.").setEphemeral(true).queue();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка обработки кнопки: ", e);
            event.reply("Произошла ошибка при обработке действия.").setEphemeral(true).queue();
        }
    }

    /**
     * Обрабатывает кнопку "ОК".
     */
    private void handleOkButton(ButtonInteractionEvent event, String buttonId) {
        String bossName = buttonId.replace("ok_", "");
        event.editMessage("Время убийства босса " + bossName + " зафиксировано.")
                .setComponents() // Убираем все кнопки
                .queue();
    }

    /**
     * Обрабатывает кнопку "Создать событие".
     */
    private void handleCreateEventButton(ButtonInteractionEvent event, String buttonId) {
        String bossName = buttonId.replace("create_event_", "");
        createEventCommand.handleButtonEvent(event, bossName);
    }


    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        try {
            if (event.getModalId().startsWith("member_search_modal:")) {
                // Обработка модального окна, если нужно
                event.reply("Модальное окно обработано").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            log.error("Ошибка обработки модального окна: ", e);
            event.reply("Произошла ошибка при обработке формы.").setEphemeral(true).queue();
        }
    }
}
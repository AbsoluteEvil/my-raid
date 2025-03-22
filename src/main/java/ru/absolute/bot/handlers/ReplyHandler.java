package ru.absolute.bot.handlers;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

@FunctionalInterface
public interface ReplyHandler {
    void reply(String message, StringSelectMenu dropMenu, Button skipButton, Button confirmButton);
}
package ru.absolute.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.commands.CreateEventCommand;
import ru.absolute.bot.commands.ShowEventsCommand;
import ru.absolute.bot.handlers.ButtonHandler;
import ru.absolute.bot.handlers.CommandHandler;
import ru.absolute.bot.services.EventService;
import ru.absolute.bot.services.GoogleSheetsService;

import javax.security.auth.login.LoginException;
import java.io.InputStream;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        final Logger logger = LoggerFactory.getLogger(GoogleSheetsService.class);
        Properties properties = new Properties();
        GoogleSheetsService googleSheetsService = new GoogleSheetsService();

        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Ошибка: Файл config.properties не найден.");
                System.exit(1);
            }
            properties.load(input);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        String discordToken = properties.getProperty("discord.token");

        if (discordToken == null) {
            System.err.println("Ошибка: Токен Discord или Google credentials не заданы в config.properties.");
            System.exit(1);
        }

        try {
            CreateEventCommand createEventCommand = new CreateEventCommand();
            EventService eventService = new EventService(); // Создаем EventService
            ShowEventsCommand showEventsCommand = new ShowEventsCommand();

            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES, // Доступ к сообщениям на сервере
                            GatewayIntent.MESSAGE_CONTENT, // Доступ к содержимому сообщений
                            GatewayIntent.GUILD_MEMBERS   // Доступ к информации об участниках сервера
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL) // Кэшировать всех участников
                    .setActivity(Activity.playing("Raid Boss Manager"))
                    .addEventListeners(
                            new CommandHandler(),
                            new ButtonHandler(createEventCommand, eventService, showEventsCommand))
                    .build();

            // Регистрируем команды
            jda.updateCommands().addCommands(
                    Commands.slash("k", "Отметить убийство босса")
                            .addOption(OptionType.STRING, "boss_name", "Имя босса", true, true), // true для автозаполнения
                    Commands.slash("create_event", "Создать событие для босса")
                            .addOption(OptionType.STRING, "boss_name", "Имя босса", true, true), // true для автозаполнения
                    Commands.slash("show_events", "Показать список событий")
                            .addOption(OptionType.STRING, "status", "Статус события (IN_PROGRESS/DONE)", false),
                    Commands.slash("show","Показать список ближайших боссов")
            ).queue();
            logger.info("Бот запущен и команды зарегистрированы.");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
package ru.absolute.bot;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import ru.absolute.bot.clients.GoogleSheetsClient;
import ru.absolute.bot.commands.*;
import ru.absolute.bot.dao.BossDao;
import ru.absolute.bot.dao.EventDao;
import ru.absolute.bot.dao.ItemsDao;
import ru.absolute.bot.handlers.ButtonHandler;
import ru.absolute.bot.handlers.CommandHandler;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;
import ru.absolute.bot.utils.ConfigLoader;

@Slf4j
public class Main {
    public static void main(String[] args) {
        // Получаем токен Discord
        String discordToken = ConfigLoader.getProperty("DISCORD_TOKEN");
        if (discordToken == null) {
            log.error("Ошибка: Токен Discord не задан в config.properties.");
            System.exit(1);
        }

        try {
            // Инициализация GoogleSheetsClient
            GoogleSheetsClient googleSheetsClient = new GoogleSheetsClient();

            // Инициализация DAO
            BossDao bossDao = new BossDao(googleSheetsClient, ConfigLoader.getProperty("GOOGLE_BOSSES_SHEET"));
            ItemsDao itemsDao = new ItemsDao(googleSheetsClient, ConfigLoader.getProperty("GOOGLE_ITEMS_SHEET"));
            EventDao eventDao = new EventDao(googleSheetsClient, ConfigLoader.getProperty("GOOGLE_EVENTS_SHEET"));

            // Инициализация сервисов
            BossService bossService = new BossService(bossDao, itemsDao);
            EventService eventService = new EventService(eventDao);

            // Создаем и настраиваем JDA
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_VOICE_STATES,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setActivity(Activity.playing("Line][age MyWay Bot"))
                    .build();

            // Ожидаем полной загрузки
            jda.awaitReady();

            // Инициализация команд
            KillCommand killCommand = new KillCommand(bossService);
            CreateEventCommand createEventCommand = new CreateEventCommand(eventService, bossService);
            EditEventCommand editEventCommand = new EditEventCommand(eventService, bossService);
            ShowCommand showCommand = new ShowCommand(bossService, jda);
            ShowEventsCommand showEventsCommand = new ShowEventsCommand(eventService, bossService);


            // Инициализация обработчиков
            CommandHandler commandHandler = new CommandHandler(
                    killCommand,
                    createEventCommand,
                    editEventCommand,
                    showCommand,
                    showEventsCommand
            );
            ButtonHandler buttonHandler = new ButtonHandler(createEventCommand, editEventCommand);

            // Добавляем обработчики после инициализации всех команд
            jda.addEventListener(commandHandler, buttonHandler);

            // Регистрируем команды
            jda.updateCommands().addCommands(
                    Commands.slash("k", "Отметить убийство босса")
                            .addOption(OptionType.STRING, "boss_name", "Имя босса", true, true)
                            .addOption(OptionType.STRING, "time", "Время убийства (например, 12:30 или 2023-10-10 12:30)", false),

                    Commands.slash("create_event", "Создать событие для босса")
                            .addOption(OptionType.STRING, "boss_name", "Имя босса", true, true),

                    Commands.slash("show_events", "Показать список событий")
                            .addOption(OptionType.STRING, "status", "Статус события (IN_PROGRESS/DONE)", false),

                    Commands.slash("edit_event", "Редактировать событие")
                            .addSubcommands(
                                    new SubcommandData("status", "Изменить статус события")
                                            .addOption(OptionType.STRING, "id", "ID события", true, true)
                                            .addOption(OptionType.STRING, "status", "Новый статус (IN_PROGRESS/DONE)", true, true),

                                    new SubcommandData("edit_members", "Редактировать список участников")
                                            .addOption(OptionType.STRING, "id", "ID события", true, true)
                            )
            ).queue();

            log.info("Бот запущен и команды зарегистрированы.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                showCommand.shutdown();
                log.info("Бот завершает работу...");
            }));

        } catch (Exception e) {
            log.error("Ошибка при запуске бота", e);
        }
    }
}
package ru.absolute.bot.services;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.*;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.models.Boss;
import ru.absolute.bot.models.Event;
import ru.absolute.bot.models.EventStatus;
import ru.absolute.bot.models.Item;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public class GoogleSheetsService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsService.class);
    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "src/main/resources/credentials.json";
    Properties properties = new Properties();


    private final String spreadsheetId ;
    private final String bossesSheetName;
    private final String eventsSheetName;
    private final String itemsSheetName;
    private Sheets sheetsService;

    @SneakyThrows
    public GoogleSheetsService() {
        // Загрузка свойств из файла конфигурации, находящегося в ресурсах
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new FileNotFoundException("Файл конфигурации application.properties не найден в ресурсах.");
            }
            properties.load(input);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке файла конфигурации: " + e.getMessage());
            throw e;
        }

        this.spreadsheetId = properties.getProperty("google.spreadsheetId");
        this.bossesSheetName = properties.getProperty("google.bossesSheet");
        this.eventsSheetName = properties.getProperty("google.eventsSheet");
        this.itemsSheetName = properties.getProperty("google.itemsSheet");

        try {
            logger.info("Попытка подключения к Google Sheets...");
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                    .createScoped(SCOPES);

            sheetsService = new Sheets.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Google Sheets API Java")
                    .build();
            logger.info("Успешно подключено к Google Sheets.");
        } catch (IOException e) {
            logger.error("Ошибка при подключении к Google Sheets: " + e.getMessage());
        }
    }


    /**
     * Обновляет время убийства босса.
     */
    public void updateBoss(Boss boss) throws IOException {
        // Получаем все строки из таблицы
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, bossesSheetName)
                .execute();

        // Ищем строку, где первый столбец (ID) совпадает с ID босса
        int rowIndex = -1;
        for (int i = 1; i < response.getValues().size(); i++) { // Пропускаем заголовок
            List<Object> row = response.getValues().get(i);
            if (row.get(0).toString().equals(String.valueOf(boss.getId()))) {
                rowIndex = i + 1; // Индекс строки в Google Sheets начинается с 1
                break;
            }
        }

        if (rowIndex == -1) {
            throw new IllegalArgumentException("Босс с ID " + boss.getId() + " не найден в таблице.");
        }

        // Формируем тело запроса для обновления строки
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(
                        Arrays.asList(
                                boss.getId(),
                                boss.getName(),
                                boss.getLevel(),
                                boss.getKillTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        )
                ));

        // Обновляем строку
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, bossesSheetName + "!A" + rowIndex, body)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Создает новое событие.
     */
    public void createEvent(Event event) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(
                        Arrays.asList(
                                event.getId(),
                                event.getDate().toString(),
                                event.getBossName(),
                                event.getDrop(),
                                String.join(",", event.getMembers()),
                                event.getNumberOfMembers(),
                                event.getStatus().toString()
                        )
                ));

        sheetsService.spreadsheets().values()
                .append(spreadsheetId, eventsSheetName + "!A1", body)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Получает всех боссов из таблицы.
     */
    public List<Boss> getAllBosses() throws IOException {
        logger.info("Получение списка боссов из таблицы...");
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, bossesSheetName)
                .execute();

        List<Boss> bosses = new ArrayList<>();
        for (int i = 1; i < response.getValues().size(); i++) { // Пропустить первую строку (заголовки)
            List<Object> row = response.getValues().get(i);

            // Проверяем, что строка не пустая и boss_name не пустой
            if (row == null || row.isEmpty() || row.get(1) == null || row.get(1).toString().trim().isEmpty()) {
                break; // Прерываем цикл при первой пустой строке
            }

            Boss boss = createBossFromRow(row);
            if (boss != null) {
                bosses.add(boss);
            }
        }

        logger.info("Успешно получено {} боссов.", bosses.size());
        return bosses;
    }

    /**
     * Получает все события из таблицы.
     */
    public List<Event> getEventsByStatus(EventStatus status){
        try {
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, eventsSheetName)
                    .execute();

            List<Event> events = processTableData(response, this::createEventFromRow);

            if (status != null) {
                events = events.stream()
                        .filter(event -> event.getStatus() == status)
                        .collect(Collectors.toList());
            }

            return events;
        } catch (Exception e) {
            logger.error("Ошибка при получении событий из Google Sheets", e);
            return Collections.emptyList(); // Возвращаем пустой список в случае ошибки
        }
    }

    public Event findEventById(String id) throws IOException {
        List<Event> events = getAllEvents();
        for (Event event : events) {
            if (event.getId().equals(id)) {
                return event;
            }
        }
        return null; // Если событие не найдено
    }

    /**
     * Получает все события из таблицы.
     */
    public List<Event> getAllEvents() throws IOException {
        logger.info("Получение списка событий из таблицы...");

        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, eventsSheetName)
                .execute();

        List<Event> events = processTableData(response, this::createEventFromRow);
        logger.info("Успешно получено {} событий.", events.size());
        return events;
    }

    private <T> List<T> processTableData(ValueRange response, Function<List<Object>, T> rowProcessor) {
        List<T> result = new ArrayList<>();
        for (int i = 1; i < response.getValues().size(); i++) { // Пропустить первую строку (заголовки)
            List<Object> row = response.getValues().get(i);

            T item = rowProcessor.apply(row);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    public void updateEvent(Event event) {
    }

    private Boss createBossFromRow(List<Object> row) {
        try {
            String idStr = row.get(0).toString().trim(); // Получаем ID как строку
            int id = Integer.parseInt(idStr); // Парсим в число, если нужно

            String name = row.get(1).toString();
            int level = row.size() > 2 && !row.get(2).toString().isEmpty()
                    ? Integer.parseInt(row.get(2).toString())
                    : 0;
            LocalDateTime killTime = row.size() > 3 && !row.get(3).toString().isEmpty()
                    ? LocalDateTime.parse(row.get(3).toString().trim())
                    : null;

            List<String> itemList = new ArrayList<>();
            if (row.size() > 4 && !row.get(4).toString().isEmpty()) {
                String itemListStr = row.get(4).toString().trim();
                itemListStr = itemListStr.replace("[", "").replace("]", "");
                String[] items = itemListStr.split(",");
                for (String item : items) {
                    itemList.add(item.trim());
                }
            }

            return new Boss(id, name, level, killTime, itemList);
        } catch (Exception e) {
            logger.error("Ошибка при создании Boss из строки {}: {}", row, e.getMessage());
            return null;
        }
    }

    private Event createEventFromRow(List<Object> row) {
        try {
            String id = row.get(0).toString();
            LocalDate date = LocalDate.parse(row.get(1).toString());
            String bossName = row.get(2).toString();
            String drop = row.get(3).toString();
            List<String> members = Arrays.asList(row.get(4).toString().split(","));

            // Обработка пустого значения для numberOfMembers
            int numberOfMembers = 0; // Значение по умолчанию
            if (row.size() > 5 && !row.get(5).toString().isEmpty()) {
                numberOfMembers = Integer.parseInt(row.get(5).toString());
            }

            // Обработка пустого значения для status
            EventStatus eventStatus = EventStatus.IN_PROGRESS; // Значение по умолчанию
            if (row.size() > 6 && !row.get(6).toString().isEmpty()) {
                eventStatus = EventStatus.valueOf(row.get(6).toString());
            }

            return new Event(id, date, bossName, drop, members, numberOfMembers, eventStatus);
        } catch (Exception e) {
            logger.error("Ошибка при создании Event из строки {}: {}", row, e.getMessage());
            return null; // Возвращаем null, если строка некорректна
        }
    }

    public List<String> getDropsByBossName(String bossName) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, itemsSheetName)
                .execute();

        List<String> drops = new ArrayList<>();
        for (int i = 1; i < response.getValues().size(); i++) {
            List<Object> row = response.getValues().get(i);
            if (row.get(0).toString().equalsIgnoreCase(bossName)) {
                drops.add(row.get(1).toString());
            }
        }
        return drops;
    }

    public String getItemNameById(String itemId) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, itemsSheetName)
                .execute();

        for (int i = 1; i < response.getValues().size(); i++) { // Пропускаем заголовок
            List<Object> row = response.getValues().get(i);
            if (row.get(0).toString().equals(itemId)) { // Сравниваем ID
                return row.get(1).toString(); // Возвращаем название вещи
            }
        }

        return null; // Если вещь не найдена
    }

    public List<List<Object>> getItemsByIds(List<String> itemIds) throws IOException {
        // Формируем запрос для получения строк с нужными ID
        String range = itemsSheetName + "!A2:B"; // Предполагаем, что заголовок в первой строке
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        // Фильтруем строки по ID
        List<List<Object>> filteredRows = new ArrayList<>();
        for (List<Object> row : response.getValues()) {
            if (row.size() >= 2 && itemIds.contains(row.get(0).toString())) {
                filteredRows.add(row);
            }
        }

        return filteredRows;
    }

    private Map<String, String> itemsMap; // Кэш для хранения вещей (ID -> Name)
    private long lastUpdateTime = 0;
    private static final long CACHE_EXPIRATION_TIME = 30 * 60 * 1000; // 30 минут

    @SneakyThrows
    public Map<String, String> getItemsMap() {
        long currentTime = System.currentTimeMillis();
        if (itemsMap == null || currentTime - lastUpdateTime > CACHE_EXPIRATION_TIME) {
            // Обновляем кэш
            this.itemsMap = getAllItems();
            this.lastUpdateTime = currentTime;
            logger.info("Кэш вещей обновлен. Загружено {} записей.", itemsMap.size());
        }
        return itemsMap;
    }

    private Map<String, String> getAllItems() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, itemsSheetName)
                .execute();

        Map<String, String> itemsMap = new HashMap<>();
        for (int i = 1; i < response.getValues().size(); i++) { // Пропускаем заголовок
            List<Object> row = response.getValues().get(i);
            if (row.size() >= 2) { // Проверяем, что строка содержит ID и название
                String itemId = row.get(0).toString();
                String itemName = row.get(1).toString();
                itemsMap.put(itemId, itemName);
            }
        }

        return itemsMap;
    }


}

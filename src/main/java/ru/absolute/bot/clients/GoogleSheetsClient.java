package ru.absolute.bot.clients;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.absolute.bot.utils.ConfigLoader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GoogleSheetsClient {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsClient.class);
    private final Sheets sheetsService;
    private final String spreadsheetId;

    @SneakyThrows
    public GoogleSheetsClient() {

        // Загружаем конфигурацию
        this.spreadsheetId = ConfigLoader.getProperty("GOOGLE_SPREAD_SHEET_ID");

        // Инициализация Google Sheets API
        logger.info("Попытка подключения к Google Sheets...");
        String jsonString=ConfigLoader.getProperty("GOOGLE_CREDENTIALS");
        GoogleCredentials credentials = GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))
                .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));

        this.sheetsService = new Sheets.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Google Sheets API Java")
                .build();
        logger.info("Успешно подключено к Google Sheets.");
    }

    public ValueRange getValues(String range) throws IOException {
        return sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
    }

    public void updateValues(String range, ValueRange body) throws IOException {
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void appendValues(String range, ValueRange body) throws IOException {
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }
}
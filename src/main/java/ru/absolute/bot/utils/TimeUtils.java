package ru.absolute.bot.utils;

import ru.absolute.bot.models.Boss;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {
    // Форматы времени
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final Pattern TIME_ONLY_PATTERN = Pattern.compile("(\\d{1,2})(?::(\\d{1,2})(?::(\\d{1,2}))?)?");
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})(?: (\\d{1,2})(?::(\\d{1,2})(?::(\\d{1,2}))?)?)?");

    /**
     * Парсит строку времени в Instant
     * @param timeStr строка времени (форматы: "чч:мм", "чч:мм:сс", "дд.мм чч:мм", "дд.мм чч:мм:сс")
     * @return Instant
     * @throws IllegalArgumentException при неверном формате
     */
    public static Instant parseKillTime(String timeStr) throws IllegalArgumentException {
        if (timeStr.contains(".")) {
            return parseDateTimeWithCurrentYear(timeStr);
        } else {
            return parseTimeOnly(timeStr);
        }
    }

    /**
     * Форматирует Instant в строку
     */
    public static String formatTime(Instant time) {
        return DISPLAY_FORMAT.withZone(ZoneId.systemDefault()).format(time);
    }

    /**
     * Парсит время с текущей датой (форматы: "чч:мм", "чч:мм:сс")
     */
    private static Instant parseTimeOnly(String timeStr) {
        Matcher matcher = TIME_ONLY_PATTERN.matcher(timeStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Неверный формат времени. Используйте `чч:мм` или `чч:мм:сс`");
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int second = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        try {
            LocalDate today = LocalDate.now();
            return LocalDateTime.of(today.getYear(), today.getMonth(), today.getDayOfMonth(), hour, minute, second)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Неверное значение времени: " + e.getMessage());
        }
    }

    /**
     * Парсит дату и время с текущим годом (форматы: "дд.мм", "дд.мм чч:мм", "дд.мм чч:мм:сс")
     */
    private static Instant parseDateTimeWithCurrentYear(String timeStr) {
        Matcher matcher = DATE_TIME_PATTERN.matcher(timeStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Неверный формат. Используйте `дд.мм [чч:мм:сс]`");
        }

        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int hour = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        int minute = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
        int second = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;

        try {
            int year = Year.now().getValue();
            return LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Неверное значение времени: " + e.getMessage());
        }
    }



    public static LocalDateTime calculateRespawnWindowStart(Boss boss) {
        if (boss.getKillTime() == null) {
            return null;
        }
        switch (boss.getName()) {
            case "Ant Queen":
                return boss.getKillTime().plusHours(26);
            case "Core":
                return boss.getKillTime().plusHours(44);
            case "Orfen":
                return boss.getKillTime().plusHours(35);
            default:
                return boss.getKillTime().plusHours(12);
        }
    }

    public static LocalDateTime calculateRespawnWindowEnd(Boss boss) {
        LocalDateTime start = calculateRespawnWindowStart(boss);
        switch (boss.getName()) {
            case "Ant Queen":
            case "Core":
            case "Orfen":
                return start.plusHours(2);
            default:
                return start.plusHours(9);
        }
    }

    public static String formatBossRespawnStatus(Boss boss) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime respawnStart = calculateRespawnWindowStart(boss);
        LocalDateTime respawnEnd = calculateRespawnWindowEnd(boss);

        if (respawnStart == null || respawnEnd == null) {
            return null;
        }

        if (now.isAfter(respawnStart)) {
            if (now.isBefore(respawnEnd)) {
                return formatInRespawnTime(now, respawnEnd);
            }
            return formatRespawnEnded(now, respawnEnd);
        }
        return formatTimeUntilRespawn(now, respawnStart);
    }

    private static String formatInRespawnTime(LocalDateTime now, LocalDateTime respawnEnd) {
        long minutes = ChronoUnit.MINUTES.between(now, respawnEnd);
        if (minutes < 1) return "сейчас закончится  ";
        return minutes < 60 ?
                String.format("↔ %2d мин.", minutes) :
                String.format("↔ %2d ч.  ", minutes / 60);
    }

    private static String formatRespawnEnded(LocalDateTime now, LocalDateTime respawnEnd) {
        long minutesSinceRespawnEnd = ChronoUnit.MINUTES.between(respawnEnd, now);
        return String.format("%d мин. назад\n", minutesSinceRespawnEnd);
    }

    private static String formatTimeUntilRespawn(LocalDateTime now, LocalDateTime respawnStart) {
        long totalMinutes = ChronoUnit.MINUTES.between(now, respawnStart);
        return totalMinutes < 60 ?
                String.format("➡ %2d мин.", totalMinutes) :
                String.format("➡ %2d ч.  ", totalMinutes / 60);
    }

}
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

    public static String formatTimeUntilRespawn(Boss boss) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime respawnStart = calculateRespawnWindowStart(boss);
        LocalDateTime respawnEnd = calculateRespawnWindowEnd(boss);

        if (respawnStart == null || respawnEnd == null) {
            return null; // Не выводим боссов с пустым временем убийства
        }

        // Если респ уже начался
        if (now.isAfter(respawnStart) && now.isBefore(respawnEnd)) {
            long minutesUntilRespawnEnd = ChronoUnit.MINUTES.between(now, respawnEnd);
            if (minutesUntilRespawnEnd < 60) {
                return "**В респе!** До конца респа: " + minutesUntilRespawnEnd + " минут";
            } else {
                long hoursUntilRespawnEnd = ChronoUnit.HOURS.between(now, respawnEnd);
                return "**В респе!** До конца респа: " + hoursUntilRespawnEnd + " часов";
            }
        }

        // Если респ уже окончен
        if (now.isAfter(respawnEnd)) {
            long hoursSinceRespawnEnd = ChronoUnit.HOURS.between(respawnEnd, now);
            if (hoursSinceRespawnEnd < 1) {
                return "Респ окончен";
            } else {
                return null; // Не выводим босса, если респ окончен более часа назад
            }
        }

        // Если респ еще не начался
        long hoursUntilRespawnStart = ChronoUnit.HOURS.between(now, respawnStart);
        long minutesUntilRespawnStart = ChronoUnit.MINUTES.between(now, respawnStart) % 60;
        long secondsUntilRespawnStart = ChronoUnit.SECONDS.between(now, respawnStart) % 60;

        return "через " + String.format("%02d:%02d:%02d", hoursUntilRespawnStart, minutesUntilRespawnStart, secondsUntilRespawnStart);
    }
}
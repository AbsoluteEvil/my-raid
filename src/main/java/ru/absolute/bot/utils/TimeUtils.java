package ru.absolute.bot.utils;

import ru.absolute.bot.models.Boss;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeUtils {
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

    private static String formatTimeDifference(LocalDateTime start, LocalDateTime end) {
        long hours = ChronoUnit.HOURS.between(start, end);
        long minutes = ChronoUnit.MINUTES.between(start, end) % 60;
        return String.format("%d часов %d минут", hours, minutes);
    }
}
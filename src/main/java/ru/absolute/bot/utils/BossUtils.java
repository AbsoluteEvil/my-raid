package ru.absolute.bot.utils;

import ru.absolute.bot.models.Boss;

import java.util.List;
import java.util.stream.Collectors;

public class BossUtils {
    public static List<Boss> filterBossesByName(List<Boss> bosses, String partialName) {
        return bosses.stream()
                .filter(boss -> boss.getName().toLowerCase().contains(partialName.toLowerCase()))
                .collect(Collectors.toList());
    }
}
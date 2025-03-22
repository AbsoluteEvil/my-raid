package ru.absolute.bot.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class Item {
    private final int id;
    private final String name;
}



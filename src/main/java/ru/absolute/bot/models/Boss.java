package ru.absolute.bot.models;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class Boss {
    private int id;
    private String name;
    private int level;
    private LocalDateTime killTime;
    private List<String> itemList;
}
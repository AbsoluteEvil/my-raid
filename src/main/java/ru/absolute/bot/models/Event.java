package ru.absolute.bot.models;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.ToString;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class Event {
    private String id;
    private LocalDate date;
    private String bossName;
    private String drop;
    private List<String> members;
    private int numberOfMembers;
    private EventStatus status;

    public void addMember(String member) {
        if (members == null) {
            members = new ArrayList<>();
        }
        members.add(member);
        numberOfMembers = members.size(); // Обновляем количество участников
    }

    public void removeMember(String member) {
        if (members != null) {
            members.remove(member);
            numberOfMembers = members.size(); // Обновляем количество участников
        }
    }
}


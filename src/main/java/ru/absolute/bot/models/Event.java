package ru.absolute.bot.models;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.ToString;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@ToString
public class Event {
    private String id;
    private LocalDate date;
    private String bossName;
    private String drop;
    private List<String> members; // Теперь это изменяемый список
    private int numberOfMembers;
    private EventStatus status;

    // Конструктор для инициализации изменяемого списка
    public Event(String id, LocalDate date, String bossName, String drop, List<String> members, int numberOfMembers, EventStatus status) {
        this.id = id;
        this.date = date;
        this.bossName = bossName;
        this.drop = drop;
        this.members = new ArrayList<>(members); // Создаем изменяемую копию
        this.numberOfMembers = this.members.size(); // Обновляем количество участников
        this.status = status;
    }

    public void addMember(String member) {
        if (members == null) {
            members = new ArrayList<>();
        }
        if (!members.contains(member)) {
            members.add(member);
            numberOfMembers = members.size();
        }
    }

    public void removeMember(String member) {
        if (members != null) {
            members.remove(member);
            numberOfMembers = members.size();
        }
    }

    public List<String> getMembers() {
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }
}


package ru.absolute.bot.models;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.ToString;
import java.time.LocalDate;
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
        this.members.add(member);
        this.numberOfMembers++;
    }

    public void removeMember(String member) {
        this.members.remove(member);
        this.numberOfMembers--;
    }
}


package ru.absolute.bot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import ru.absolute.bot.services.BossService;
import ru.absolute.bot.services.EventService;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseCommand {
    protected final BossService bossService;
    protected final EventService eventService;

    public BaseCommand(BossService bossService, EventService eventService) {
        this.bossService = bossService;
        this.eventService = eventService;
    }

    protected List<String> getDropNames(String dropIds) {
        Map<String, String> itemsMap = bossService.getItemsMap();
        return Arrays.stream(dropIds.split(","))
                .map(String::trim)
                .map(itemsMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected String formatMembersByGroups(List<String> memberIds, Guild guild) {
        if (guild == null) {
            return String.join(", ", memberIds);
        }

        boolean hasRyblbRole = guild.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase("Ryblb&KO"));

        Map<String, List<String>> groupedMembers = new HashMap<>();

        for (String memberId : memberIds) {
            Member member = guild.getMemberById(memberId);
            if (member == null) {
                groupedMembers.computeIfAbsent("Unknown", k -> new ArrayList<>())
                        .add("Unknown User (" + memberId + ")");
                continue;
            }

            String group = "MyWay";
            if (hasRyblbRole && member.getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase("Ryblb&KO"))) {
                group = "Ryblb&KO";
            }

            groupedMembers.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(member.getEffectiveName());
        }

        return groupedMembers.entrySet().stream()
                .map(entry -> String.format("%s [%d]: %s",
                        entry.getKey(),
                        entry.getValue().size(),
                        String.join(", ", entry.getValue())))
                .collect(Collectors.joining("\n"));
    }

    protected void handleError(IReplyCallback event, String message, Exception e) {
        log.error(message, e);
        event.reply(message).setEphemeral(true).queue();
    }
}

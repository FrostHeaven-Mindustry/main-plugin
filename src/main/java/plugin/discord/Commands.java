package plugin.discord;

import arc.Events;
import arc.util.Http;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.server.ServerControl;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import plugin.configs.ConfigJson;
import plugin.discord.commands.DiscordCommandRegister;
import plugin.models.Ranks;
import plugin.database.wrappers.PlayerData;
import plugin.Utilities;
import useful.Bundle;
import java.awt.*;
import java.io.*;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import static arc.util.Strings.canParseInt;
import static arc.util.Strings.stripColors;
import static mindustry.Vars.netServer;
import static plugin.configs.ConfigJson.discordUrl;
import static plugin.discord.Bot.api;
import static plugin.discord.Embed.banEmbed;
import static plugin.models.Ranks.getRank;
import static plugin.funcs.Other.*;
import static plugin.menus.MenuHandler.loginMenu;
import static plugin.menus.MenuHandler.loginMenuFunction;
import static plugin.Utilities.findPlayerByName;

public class Commands {
    public static void load() {
        DiscordCommandRegister.create("help")
                .desc("See all available commands")
                .build((message, string) -> {
                    var paged = new PagedEmbed();
                    Utilities.splitBy(DiscordCommandRegister.commands.select(c ->!c.hidden), 5).each(s -> {
                       var page = new PagedEmbed.EmbedPage("Available commands", Color.magenta);
                       s.each(command -> {
                           page.addField(command.desc, command.name + " " + command.args);
                       });
                       paged.addPage(page);
                    });
                    paged.send(message.getChannel());
                });
        DiscordCommandRegister.create("ranks")
                .desc("See all available ranks")
                .build((message, string) -> {
                    EmbedBuilder embed = new EmbedBuilder().setTitle("Ranks").setColor(Color.decode("#00BFFF"));
                    for (Ranks.Rank rank: Ranks.Rank.values())
                        embed.addField(rank.getName(), rank.getDescription());
                    message.getChannel().sendMessage(embed);
                });
        DiscordCommandRegister.create("stats")
                .desc("See player stats")
                .args("<id>")
                .requiredArgs(1)
                .build((message, string) -> {
                    if (!canParseInt(string)) {
                        message.getChannel().sendMessage("'id' must be number!");
                        return;
                    }
                    PlayerData data = new PlayerData(Integer.parseInt(string));
                    if (!data.isExist()) {
                        message.getChannel().sendMessage("Could not find that player!");
                    } else {
                        long playtime = data.getPlaytime();
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("Information")
                                .setColor(Color.RED)
                                .addField("Name", stripColors(data.getNames().toString()))
                                .addField("ID", String.valueOf(data.getId()))
                                .addField("Rank", data.getRank().getColoredName())
                                .addField("Playtime", Bundle.formatDuration(Duration.ofMinutes(playtime)));
                        if (data.getDiscordId() != 0) {
                            embed.addField("Discord", "<@" + data.getDiscordId() + ">");
                        }
                        message.getChannel().sendMessage(embed);
                    }
                });
        DiscordCommandRegister.create("list")
                .desc("Players online")
                .build((message, string) -> {
                    StringBuilder list = new StringBuilder();
                    list.append("```Players online: ").append(Groups.player.size()).append("\n\n");
                    for (Player player : Groups.player) {
                        PlayerData data = new PlayerData(player);
                        int id = data.getId();
                        if (player.admin()) {
                            list.append("# [A] ").append(player.plainName()).append("; ID: ").append(id).append("\n");
                        } else {
                            list.append("# ").append(player.plainName()).append("; ID: ").append(id).append("\n");
                        }
                    }
                    list.append("```");
                    message.getChannel().sendMessage(list.toString());
                });
        DiscordCommandRegister.create("ban")
                .desc("Ban player")
                .args("<id|uuid> <duration> <reason...>")
                .addRole(ConfigJson.moderatorId)
                .requiredArgs(3)
                .build((message, string) -> {
                    String[] args = string.split(" ", 3);
                    if (!canParseInt(args[1])) {
                        message.getChannel().sendMessage("Please, type a number in time!");
                        return;
                    }
                    long time = Long.parseLong(args[1]);
                    Date date = new Date();
                    long banTime = date.getTime() + TimeUnit.DAYS.toMillis(time);
                    String timeUntilUnban = Bundle.formatDuration(Duration.ofDays(time));
                    PlayerData data;
                    if (canParseInt(args[0])) data = new PlayerData(Integer.parseInt(args[0]));
                    else data = new PlayerData(args[0]);
                    if (!data.isExist()) {
                        message.getChannel().sendMessage("Could not find that player.");
                        return;
                    }
                    Player plr = Groups.player.find(p -> p.uuid().equals(data.getUuid()));
                    if (plr == null) {
                        Log.info("PlayerData is offline, not kicking him");
                    } else {
                        plr.con.kick("[red]You have been banned!\n\n" + "[white]Reason: " + args[2] + "\nDuration: " + timeUntilUnban + " until unban\nIf you think this is a mistake, make sure to appeal ban in our discord: " + discordUrl, 0);
                    }
                    message.getChannel().sendMessage("Banned: " + data.getLastName());
                    Call.sendMessage(data.getLastName() + " has been banned for: " + args[2]);
                    data.setLastBanTime(banTime);
                    Bot.banchannel.sendMessage(discordBanEmbed(data, message.getAuthor().getId(), args[2], banTime, message.getAuthor().getName()));
                });
        DiscordCommandRegister.create("infoip")
                .addRole(ConfigJson.moderatorId)
                .desc("Trace ip")
                .args("<ip>")
                .requiredArgs(1)
                .build((message, string) -> {
                    var data = PlayerData.findByIp(string);
                    if (data.isEmpty()) {
                        message.getChannel().sendMessage("Can`t find player with this ip!");
                        return;
                    }
                    message.getChannel().sendMessage(Utilities.stringify(data, d -> d.getLastName() + " [" + d.getId() + "]" + " [" + d.getUuid() + "]\n"));
                });
        DiscordCommandRegister.create("info")
                .desc("Get player info")
                .args("<id>")
                .requiredArgs(1)
                .addRole(ConfigJson.moderatorId)
                .build((message, string) -> {
                    if (!canParseInt(string)) {
                        message.getChannel().sendMessage("'id' must be number!");
                        return;
                    }
                    PlayerData data = new PlayerData(Integer.parseInt(string));
                    if (data.isExist()) message.getChannel().sendMessage(Embed.infoEmbed(data));
                    else message.getChannel().sendMessage("nonexistent id!");
                });
        DiscordCommandRegister.create("adminadd")
                .desc("Admin player")
                .args("<name...>")
                .hidden(true)
                .addRole(ConfigJson.adminId)
                .requiredArgs(1)
                .build((message, string) -> {
                    Player player = findPlayerByName(string);
                    if (player == null) {
                        message.getChannel().sendMessage("Could not find that player!");
                        return;
                    }
                    if (player.admin()) {
                        message.getChannel().sendMessage("Player is already admin!");
                        return;
                    }
                    netServer.admins.adminPlayer(player.uuid(), player.usid());
                    player.admin = true;
                    message.getChannel().sendMessage("Successfully admin: " + player.plainName());
                });
        DiscordCommandRegister.create("setrank")
                .desc("Set player rank")
                .args("<id> <rank>")
                .requiredArgs(1)
                .addRole(ConfigJson.adminId)
                .build((message, string) -> {
                    String[] args = string.split(" ");
                    if (!canParseInt(args[0])) {
                        message.getChannel().sendMessage("'id' must be number!");
                        return;
                    }
                    PlayerData data = new PlayerData(Integer.parseInt(args[0]));
                    if (!data.isExist()) {
                        message.getChannel().sendMessage("No such player!");
                    } else if (getRank(args[2]) == Ranks.Rank.None) {
                        message.getChannel().sendMessage("This rank doesnt exist!");
                    } else {
                        data.setRank(args[2]);
                        message.getChannel().sendMessage("Rank has been given!");
                    }
                });
        DiscordCommandRegister.create("gameover")
                .desc("game over")
                .addRole(ConfigJson.moderatorId)
                .addRole(ConfigJson.adminId)
                .build((message, string) -> {
                    Events.fire(new EventType.GameOverEvent(Team.derelict));
                    message.getChannel().sendMessage("Gameover executed!");
                });
        DiscordCommandRegister.create("login")
                .desc("h")
                .args("<id>")
                .requiredArgs(1)
                .build((message, string) -> {
                    if (!canParseInt(string)) {
                        message.getChannel().sendMessage("'id' must be number!");
                        return;
                    }
                    PlayerData data = new PlayerData(Integer.parseInt(string));
                    if (!data.isExist()) {
                        message.getChannel().sendMessage("This player doesnt exist!");
                    } else {
                        Player player = Groups.player.find(p -> p.uuid().equals(data.getUuid()));
                        if (player == null) {
                            message.getChannel().sendMessage("This player is offline!");
                        } else {
                            loginMenuFunction(message);
                            Call.menu(player.con, loginMenu, "Request", message.getAuthor().getName() + " requests to connect your mindustry account", new String[][]{{"Connect"}, {"Cancel"}});
                            message.getChannel().sendMessage("request has been sent");
                        }
                    }
                });
        DiscordCommandRegister.create("search")
                .args("<name...>")
                .requiredArgs(1)
                .desc("Search player by name")
                .build((message, string) -> {
                    StringBuilder output = new StringBuilder();
                    output.append("```Results:\n\n");
                    for (PlayerData data : PlayerData.findByName(string))
                        output.append(data.getLastName()).append("; ID: ").append(data.getId()).append("\n");
                    output.append("```");
                    message.getChannel().sendMessage(String.valueOf(output));
                });
        DiscordCommandRegister.create("unban")
                .args("")
                .requiredArgs(1)
                .addRole(ConfigJson.moderatorId)
                .desc("Unban player")
                .build((message, string) -> {
                    if (!canParseInt(string)) {
                        message.getChannel().sendMessage("'id' must be number!");
                        return;
                    }
                    PlayerData data = new PlayerData(Integer.parseInt(string));
                    if (!data.isExist()) {
                        message.getChannel().sendMessage("Could not find that player!");
                    } else if (data.getLastBanTime() == 0L) {
                        message.getChannel().sendMessage("User is not banned!");
                    } else {
                        data.setLastBanTime(0L);
                        message.getChannel().sendMessage(data.getLastName() + " has been unbanned!");
                    }
                });
        DiscordCommandRegister.create("js")
                .addRole(ConfigJson.adminId)
                .hidden(true)
                .args("<code...>")
                .requiredArgs(1)
                .desc("run js")
                .build((message, string) -> {
                    Utilities.runJs(string, resp -> {
                        if (!resp.isEmpty()) message.getChannel().sendMessage(resp);
                    });
                });
        DiscordCommandRegister.create("exit")
                .addRole(ConfigJson.adminId)
                .desc("Exit server application")
                .build((message, string) -> {
                    api.disconnect();
                    Timer.schedule(() -> {
                        System.exit(0);
                    }, 1f);
                });
        DiscordCommandRegister.create("addmap")
                .desc("Upload map")
                .addRole(ConfigJson.adminId)
                .build((message, string) -> {
                    message.getAttachments().forEach(messageAttachment -> Http.get(String.valueOf(messageAttachment.getUrl()), response -> {
                        Vars.customMapDirectory.child(messageAttachment.getFileName()).writeBytes(response.getResult());
                        message.getChannel().sendMessage("Success!");
                    }));
                    reloadMaps();
                });
        DiscordCommandRegister.create("removemap")
                .desc("Remove map from server")
                .args(ConfigJson.adminId)
                .args("<mapname...>")
                .requiredArgs(1)
                .build((message, string) -> {
                    var map = Vars.maps.customMaps().find(m -> m.plainName().toLowerCase().equals(string));
                    if (map == null) {
                        message.getChannel().sendMessage("Can`t find this map!");
                        return;
                    }
                    map.file.delete();
                    Vars.maps.reload();
                    message.getChannel().sendMessage("Deleted " + map.file.name());
                });
        DiscordCommandRegister.create("maps")
                .desc("Maps on this server")
                .build((message, string) -> {
                    message.getChannel().sendMessage(Vars.maps.customMaps().toString("\n", Map::plainName));
                });
        DiscordCommandRegister.create("exec")
                .addRole(ConfigJson.adminId)
                .hidden(true)
                .args("<command...>")
                .desc("Execute command in server console")
                .requiredArgs(1)
                .build((message, string) -> {
                    ServerControl.instance.handleCommandString(string);
                    message.getChannel().sendMessage("Executed.");
                });
        DiscordCommandRegister.create("viewlatestlogs")
                .hidden(true)
                .addRole(ConfigJson.adminId)
                .args("<count...>")
                .desc("View last server logs")
                .build((message, string) -> {
                    int amount = 100;
                    if (canParseInt(string)) {
                        amount = Integer.parseInt(string);
                    }
                    File file = new File(Vars.dataDirectory.absolutePath() + "/logs/");
                    File chosenFile = null;
                    long lastMod = Long.MIN_VALUE;
                    for (File file1 : Objects.requireNonNull(file.listFiles())) {
                        if (file1.lastModified() > lastMod) {
                            chosenFile = file1;
                            lastMod = file1.lastModified();
                        }
                    }
                    if (chosenFile == null) return;
                    BufferedReader reader;
                    try {
                        reader = new BufferedReader(new FileReader(chosenFile));
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    List<String> list = reader.lines().toList();
                    long count = list.size();
                    List<String> newList = list.stream().skip(count - amount).toList();
                    try {
                        File readFile = new File(Vars.tmpDirectory.absolutePath() + "/readfile.txt");
                        readFile.createNewFile();
                        FileWriter writer = new FileWriter(readFile);
                        for (String line : newList){
                            writer.write(line + "\n");
                        }
                        writer.close();
                        message.getChannel().sendMessage(readFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    };
                });
        DiscordCommandRegister.create("backupdb")
                .desc("Create database backup")
                .addRole(ConfigJson.adminId)
                .hidden(true)
                .build((message, string) -> {
                    try {
                        File data2 = new File(Vars.tmpDirectory.absolutePath() + "/mindustry");
                        data2.delete();
                        Runtime.getRuntime().exec("mongodump -d mindustry -o " + Vars.tmpDirectory.absolutePath());
                        Timer.schedule(() -> {
                            for (File file : data2.listFiles()) {
                                message.getChannel().sendMessage(file);
                            }
                        }, 2);

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        DiscordCommandRegister.create("proc")
                .desc("See process info")
                .addRole(ConfigJson.adminId)
                .build((message, string) -> {
                    ProcessHandle handle = ProcessHandle.current();
                    message.getChannel().sendMessage("```\nPROCESS INFO:\n\nPID: " +
                            handle.pid() + "\nCOMMAND: " + handle.info().command().get() +
                            "\nCOMMAND LINE: " + handle.info().commandLine().get() +
                            "\nSTARTINSTANT: " + handle.info().startInstant().get() + "\nOWNER: " +
                            handle.info().user().get() + "\n```");
                });
    }
}

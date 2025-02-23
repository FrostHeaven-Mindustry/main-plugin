package plugin.discord;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Http;
import arc.util.Log;
import arc.util.Timer;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.server.ServerControl;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import plugin.ConfigJson;
import plugin.models.PlayerData;
import useful.Bundle;

import java.awt.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static arc.util.Strings.*;
import static mindustry.Vars.mods;
import static mindustry.Vars.netServer;
import static plugin.ConfigJson.discordUrl;
import static plugin.Plugin.newCollection;
import static plugin.discord.DiscordFunctions.*;
import static plugin.discord.Embed.banEmbed;
import static plugin.discord.Warnings.noDataFound;
import static plugin.etc.Ranks.Rank;
import static plugin.etc.Ranks.getRank;
import static plugin.functions.MongoDB.MongoDbPlayerRankCheck;
import static plugin.functions.MongoDB.MongoDbUpdate;
import static plugin.functions.Other.*;
import static plugin.utils.FindDocument.getPlayerData;
import static plugin.utils.FindDocument.getPlayerDataAnyway;
import static plugin.utils.MenuHandler.loginMenu;
import static plugin.utils.MenuHandler.loginMenuFunction;
import static plugin.utils.Utilities.findPlayerByName;
import static plugin.utils.Checks.isAdmin;

@SuppressWarnings("unused")
public class Bot {
    // variables for load function
    public static DiscordApi api;
    public static TextChannel channel;
    public static TextChannel banchannel;
    public static String prefix = ConfigJson.prefix;
    // main bot
    public static void load(){
        api =  new DiscordApiBuilder()
                .setToken(ConfigJson.token)
                .addIntents(Intent.GUILDS, Intent.MESSAGE_CONTENT, Intent.GUILD_MESSAGES)
                .login()
                .join();
        api.addMessageCreateListener(Bot::onMessageCreate);
/*        api.addSlashCommandCreateListener(Bot::addSlashCommandListener);*/
        channel = api.getChannelById(ConfigJson.logChannelId).get().asTextChannel().get();
        banchannel = api.getChannelById(ConfigJson.banLogChannelId).get().asTextChannel().get();
/*        registerSlashCommands();*/
        init();
    }
    // the stuff that logs if bot is started and also some random events
    public static void init(){
        Log.info("Bot started");
        Events.on(EventType.PlayerChatEvent.class, event  -> {
            if (event.message.startsWith("/")) {
                return;
            }
            channel.sendMessage("`" + event.player.plainName() + ": " + event.message + "`");
        });
        Events.on(EventType.PlayerJoin.class, event ->
                Timer.schedule(() -> {
            PlayerData data = getPlayerData(event.player.uuid());
            if (data != null) {
                channel.sendMessage("`" + event.player.plainName() + " (" + data.id + ")" + " joined the server!" + "`");
            }
        }, 0.2f));
        Events.on(EventType.PlayerLeave.class, event ->
                Timer.schedule(() -> {
                    PlayerData user = getPlayerData(event.player.uuid());
            channel.sendMessage("`" + event.player.plainName()  + " ("+ user.id + ")" +" left the server!" + "`");
        }, 0.2f));
    }
     // creating listener once message is created
    private static void onMessageCreate(MessageCreateEvent listener) {
        /*if(!state.is(GameState.State.playing)){
            listener.getChannel().sendMessage("Server is not running");
            returnl
        }
        if(listener.getServer().isEmpty()){
            listener.getChannel().sendMessage("Cant use commands in DM's");
            return;
        }*/
        if (listener.getChannel() == channel && listener.getMessageAuthor().isRegularUser()){
            Call.sendMessage("[blue][" + listener.getMessageAuthor().getDisplayName() + "[blue]]: [white]" + listener.getMessageContent());
        }
        if(listener.getMessageAuthor().isBotUser()){
            return;
        }
        if(!listener.getMessageContent().contains(prefix)){
            return;
        }
        switch(listener.getMessageContent().split(" ")[0].replace(prefix, "")){
            case "help" -> {
                String response = "```" +
                        prefix + "stats <playerIdOrName...> -> get statistics about player\n" +
                        prefix + "list -> list all active players on server\n" +
                        prefix + "ban <playerIdOrNameOrUUID> <days> <reason...> -> bans the player\n" +
                        prefix + "unban <playerIdOrNameOrUUID...> -> unbans the player\n" +
                        prefix + "js <code...> -> executes code\n" +
                        prefix + "gameover -> executes gameover\n" +
                        prefix + "adminadd <playerName...> -> gives admin to player\n" +
                        prefix + "login <playerIdOrName...> -> connects discord account and mindustry account\n" +
                        prefix + "search <playerName...> -> search players using their name\n" +
                        prefix + "ranks -> get all ranks on server\n" +
                        "```";
                listener.getChannel().sendMessage(response);
            }
            case "ranks" -> {
                String response = "```" +
                        "Player -> Basic rank that given to all players on our server\n" +
                        "Verified -> In order to get it you should connect your mindustry account to discord using /login\n" +
                        "Moderator -> Administrator of our servers.\n" +
                        "JS (JavaScript) -> People that have access to game console and javascript execution\n" +
                        "Administrator -> Rank of head admins, has access to everything" +
                        "```";
                listener.getChannel().sendMessage(response);
            }
            case "hi" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                String arg = readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);
                listener.getChannel().sendMessage("Hi! " + arg);
            }
            case "stats" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                String id = readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);
                PlayerData data = getPlayerDataAnyway(id);
                if (data == null){
                    listener.getChannel().sendMessage("Could not find that player!");
                    return;
                }
                long playtime = data.playtime;
                String discordId = String.valueOf(data.discordId);
                if (discordId.equals("0")){
                    discordId = "none";
                }
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Information")
                        .setColor(Color.RED)
                        .addField("Name", stripColors(data.name))
                        .addField("ID", String.valueOf(data.id))
                        .addField("Rank", data.rank)
                        .addField("Achievements", data.achievements.toString())
                        .addField("Playtime",  Bundle.formatDuration(Duration.ofMinutes(playtime)));
                        if (discordId != "none"){
                            embed.addField("Discord", "<@" +discordId +">");
                        }
                listener.getChannel().sendMessage(embed);
            }
            case "list" -> {
                StringBuilder list = new StringBuilder();
                list.append("```Players online: ").append(Groups.player.size()).append("\n\n");
                for (Player player : Groups.player){
                    PlayerData user = getPlayerData(player.uuid());
                    int id = user.id;
                    if (player.admin()){
                        list.append("# [A] ").append(player.plainName()).append("; ID: ").append(id).append("\n");
                    } else {
                        list.append("# ").append(player.plainName()).append("; ID: ").append(id).append("\n");
                    }
                }
                list.append("```");
                listener.getChannel().sendMessage(String.valueOf(list));
            }
            case "ban" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 3)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                if (!isModerator(listener)){
                    return;
                }

                String response;

                String id = listener.getMessageContent().split(" ")[1];
                String reason = readValueFromArraySeparated(listener.getMessageContent().split(" "), 3, listener.getMessageContent().split(" ").length);
                if (!canParseInt(listener.getMessageContent().split(" ")[2])){
                    listener.getChannel().sendMessage("Please, type a number in time!");
                }
                long time = Long.parseLong(listener.getMessageContent().split(" ")[2]);
                Date date = new Date();
                long banTime = date.getTime() + TimeUnit.DAYS.toMillis(time);
                String timeUntilUnban = Bundle.formatDuration(Duration.ofDays(time));
                PlayerData data= getPlayerDataAnyway(id);
                if (data == null) {
                    response = "Could not find that player.";
                    listener.getChannel().sendMessage(response);
                    return;
                }
                Player plr = Groups.player.find(p -> p.uuid().equals(data.uuid));
                if (plr.isAdmin()) {
                    Log.info("Player is admin, he can't be banned");
                }
                if (plr == null) {
                    Log.info("Player is offline, not kicking him");
                } else {
                    plr.con.kick("[red]You have been banned!\n\n" + "[white]Reason: " + reason + "\nDuration: " + timeUntilUnban + " until unban\nIf you think this is a mistake, make sure to appeal ban in our discord: " + discordUrl, 0);
                }
                listener.getChannel().sendMessage("Banned: " + data.name);

                Call.sendMessage(data.name + " has been banned for: " + reason);
                data.lastBan = banTime;
                MongoDbUpdate(data);
                Bot.banchannel.sendMessage(banEmbed(data, reason, banTime, listener.getMessageAuthor().getName()));
            }
            case "adminadd" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                if (!isModerator(listener)){
                    return;
                }
                String name = readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);
                Player player = findPlayerByName(name);
                if (player == null){
                    listener.getChannel().sendMessage("Could not find that player!"); return;
                }
                if (player.admin()){
                    listener.getChannel().sendMessage("Player is already admin!"); return;
                }
                netServer.admins.adminPlayer(String.valueOf(player.uuid()), player.usid());
                player.admin = true;
                listener.getChannel().sendMessage("Successfully admin: " + player.plainName());
            }
            case "setrank" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 2)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                if (!isAdmin(listener)){
                    return;
                }
                PlayerData data = getPlayerDataAnyway(listener.getMessageContent().split(" ")[1]);
                String rank = listener.getMessageContent().split(" ")[2];
                if (data == null){
                    listener.getChannel().sendMessage("No such player!");
                    return;
                }
                if (getRank(rank) == Rank.None){
                    listener.getChannel().sendMessage("This rank doesnt exist!");
                    return;
                }
                data.rank = rank;
                MongoDbUpdate(data);
                listener.getChannel().sendMessage("Rank has been given!");
                Player player = Groups.player.find(p -> p.uuid().equals(data.uuid));
                if (player == null){
                    return;
                }
                MongoDbPlayerRankCheck(data.uuid);
            }
            case "gameover" -> {
                if (!isModerator(listener)){
                    return;
                }
                Events.fire(new EventType.GameOverEvent(Team.derelict));
                listener.getChannel().sendMessage("Gameover executed!");
            }
            case "login" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                String id = readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);
                PlayerData data = getPlayerDataAnyway(id);
                if (data == null){
                    listener.getChannel().sendMessage("This player doesnt exist!");
                    return;
                }
                Player player = Groups.player.find(p -> p.uuid().equals(data.uuid));
                if (player == null){
                    listener.getChannel().sendMessage("This player is offline!");
                    return;
                }
                loginMenuFunction(listener);
                Call.menu(player.con, loginMenu, "Request", listener.getMessageAuthor().getName() + " requests to connect your mindustry account", new String[][]{{"Connect"}, {"Cancel"}});
                listener.getChannel().sendMessage("request has been sent");
            }
            case "search" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                String name= readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);

                StringBuilder list = new StringBuilder();
                Pattern pattern = Pattern.compile(".?" +name + ".?", Pattern.CASE_INSENSITIVE);
                list.append("```Results:\n\n");
                try (MongoCursor<PlayerData> cursor = newCollection.find(Filters.regex("name", pattern)).limit(25).iterator()) {
                    while (cursor.hasNext()) {
                        PlayerData csr = cursor.next();
                        list.append(csr.name).append("; ID: ").append(csr.id).append("\n");
                    }
                }
                list.append("```");
                listener.getChannel().sendMessage(String.valueOf(list));
            }
            case "unban" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                if (!isModerator(listener)){
                    return;
                }
                String id = readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);
                PlayerData data = getPlayerDataAnyway(id);
                if (data == null){
                    listener.getChannel().sendMessage("Could not find that player!"); return;
                }
                if (data.lastBan == 0L){
                    listener.getChannel().sendMessage("User is not banned!"); return;
                }
                data.lastBan = 0L;
                MongoDbUpdate(data);
                listener.getChannel().sendMessage(data.name + " has been unbanned!");
            }
            case "js" -> {
                if (!inBounds(listener.getMessageContent().split(" "), 1)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                if (!isAdmin(listener)){
                    return;
                }
                String cmd = readValueFromArraySeparated(listener.getMessageContent().split(" "), 1, listener.getMessageContent().split(" ").length);
                Core.app.post(() -> {
                    String output = mods.getScripts().runConsole(cmd);
                    listener.getChannel().sendMessage(output);
                });
            }
            case "exit" -> {
                if (!isAdmin(listener)){
                    return;
                }
                api.disconnect();
                Timer.schedule(()-> {
                    System.exit(0);
                }, 1f);
            }
            case "giveach" -> {
                if (!isAdmin(listener)){
                    return;
                }
                if (!inBounds(listener.getMessageContent().split(" "), 2)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                String id = listener.getMessageContent().split(" ")[1];
                PlayerData data = getPlayerDataAnyway(id);
                if (data == null) {
                    noDataFound(listener);
                    return;
                }
                String achName = readValueFromArraySeparated(listener.getMessageContent().split(" "), 2, listener.getMessageContent().split(" ").length);
                java.util.List<String> yes = data.achievements;
                ArrayList<String> newArray = new ArrayList<>(yes);
                newArray.add(achName);
                data.achievements = newArray;
                MongoDbUpdate(data);
            }
            case "removeach" -> {
                if (!isAdmin(listener)){
                    return;
                }
                if (!inBounds(listener.getMessageContent().split(" "), 2)){
                    listener.getChannel().sendMessage("Not enough arguments!"); return;
                }
                String id = listener.getMessageContent().split(" ")[1];
                PlayerData data = getPlayerDataAnyway(id);
                if (data == null) {
                    noDataFound(listener);
                    return;
                }
                String achName = readValueFromArraySeparated(listener.getMessageContent().split(" "), 2,  listener.getMessageContent().split(" ").length);
                java.util.List<String> yes = data.achievements;
                ArrayList<String> newArray = new ArrayList<>(yes);
                newArray.remove(achName);
                data.achievements = newArray;
                MongoDbUpdate(data);
            }
            case "addmap" -> {
                if (!isAdmin(listener)) return;
               List<MessageAttachment> atcs = listener.getMessage().getAttachments();
               atcs.forEach(messageAttachment ->
                       Http.get(String.valueOf(messageAttachment.getUrl()), response -> {
                           var file = Vars.customMapDirectory.child(messageAttachment.getFileName());
                           file.writeBytes(response.getResult());
                           listener.getChannel().sendMessage("Success!");
                       })
                       );
                reloadMaps();
            }
            case "removemap" -> {
                if (!isAdmin(listener)) return;
                if (!(listener.getMessageContent().split(" ").length >= 2)) return;
                String name = readValueFromArraySeparated(listener.getMessageContent().split(" ") ,1, listener.getMessageContent().split(" ").length);
                File dir = new File(Vars.customMapDirectory.absolutePath());
                File file = Arrays.stream(Objects.requireNonNull(dir.listFiles())).filter(file1 -> file1.getName().contains(name)).findFirst().get();
                if (file.exists()){
                    var checkIfDeleted = file.delete();
                    if (checkIfDeleted) {
                        listener.getChannel().sendMessage("Map has been deleted!");
                        reloadMaps();
                    } else{
                        listener.getChannel().sendMessage("Couldnt delete map!");
                    }
                }


            }
            case "listmaps" -> {
                File dir = new File(Vars.customMapDirectory.absolutePath());
                var list = new StringBuilder();
                list.append("```MAPS:\n\n");
                for (File file : dir.listFiles())
                    list.append(file.getName() + System.lineSeparator());
                listener.getChannel().sendMessage(list + "```");
            }
            case "exec" -> {
                if (!isAdmin(listener)) return;
                ServerControl.instance.handleCommandString(readValueFromArraySeparated(listener.getMessageContent().split(" "), 1 , listener.getMessageContent().split(" ").length));
                CommandHandler.CommandResponse result = ServerControl.instance.handler.handleMessage(listener.getMessageContent().split(" ")[1]);
                listener.getChannel().sendMessage("```" + result.runCommand + "```");
            }
            case "viewlatestlogs" -> {
                if (!isAdmin(listener)) return;
                int amount = 100;
                if (listener.getMessageContent().split(" ").length != 1) {
                    if (!canParseInt(listener.getMessageContent().split(" ")[1])) {
                        listener.getChannel().sendMessage("Type a number!");
                        return;
                    }
                    amount = Integer.parseInt(listener.getMessageContent().split(" ")[1]);
                }
                File file = new File(Vars.dataDirectory.absolutePath() + "/logs/");
                File chosenFile = null;
                long lastMod = Long.MIN_VALUE;
                for (File file1 : Objects.requireNonNull(file.listFiles())){
                    if (file1.lastModified() > lastMod){
                        chosenFile = file1;
                        lastMod = file1.lastModified();
                    }
                }
                if (chosenFile == null) return;
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(chosenFile));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
                List<String> list = reader.lines().toList();
                long count = list.size();
                List<String> newList = list.stream().skip(count - amount).toList();
                try { createAndSendTempFile(listener, newList); } catch (IOException e) { throw new RuntimeException(e); };

            }
            case "proc" -> {
                if (!isAdmin(listener)) return;
                ProcessHandle handle = ProcessHandle.current();
                String info = "```PROCESS INFO:" + System.lineSeparator() + System.lineSeparator() +
                        "PID: " + handle.pid() + System.lineSeparator() +
                        "COMMAND: " + handle.info().command().get() + System.lineSeparator() +
                        "COMMAND LINE: " + handle.info().commandLine().get() + System.lineSeparator() +
                        "STARTINSTANT: " + handle.info().startInstant().get() + System.lineSeparator() +
                        "OWNER: " + handle.info().user().get() + System.lineSeparator() +
                        "```";
                listener.getChannel().sendMessage(info);
            }
            case "backupdb" -> {
                if (!isAdmin(listener)) return;
                try {
                    File data2 = new File(Vars.tmpDirectory.absolutePath() + "/mindustry");
                    data2.delete();
                    Runtime.getRuntime().exec("mongodump -d mindustry -o " + Vars.tmpDirectory.absolutePath());
                    Timer.schedule(() -> {
                        for (File file : data2.listFiles()) {
                            listener.getChannel().sendMessage(file);
                        }
                    },2);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case "conhelp" -> {
                if (!isAdmin(listener)) return;
                String help = "```CONSOLE COMMANDS:\n\n" +
                        "Note: do not use some of commands at public chats!\n\n" +
                        prefix + "exit - exits the server" +
                        prefix + "backupdb - Dumps database and sends it to discord\n" +
                        prefix + "proc - Views status of server processs\n" +
                        prefix + "addmap <Attachments> - Adds maps to the server\n" +
                        prefix + "removemap <mapname> - Removes map from the server\n" +
                        prefix + "viewlatestlogs <lines> - Views latest logs\n" +
                        prefix + "listmap - Lists all map files\n" +
                        prefix + "exec <command> - Executes console command\n" +
                        prefix + "giveach <plr> <ach> - give special achievement\n" +
                        prefix + "removeach <plr> <ach> - removes special achievement\n" +
                        prefix + "setrank <playerIdOrName> <rankid> -> set rank to player```\n";
                listener.getChannel().sendMessage(help);
            }
            }
        }
    }
    // registers slash commands so user can see them and use
    /*private static void registerSlashCommands() {
        SlashCommand banCommand = SlashCommand.with("ban", "Bans the player",
                        Arrays.asList(
                                SlashCommandOption.create(
                                        SlashCommandOptionType.STRING,
                                        "idOrNameOrUUID",
                                        "id or name or uuid of the player",
                                        true
                                ),
                                SlashCommandOption.create(
                                        SlashCommandOptionType.LONG,
                                        "time",
                                        "Duration of ban (in days)",
                                        true
                                ),
                                SlashCommandOption.create(
                                        SlashCommandOptionType.STRING,
                                        "reason",
                                        "Reason of ban",
                                        true
                                )
                        )
        ).createGlobal(api).join();
        SlashCommand listCommand = SlashCommand.with("list", "Lists the players"
        ).createGlobal(api).join();
        SlashCommand adminaddCommand = SlashCommand.with("adminadd", "gives admin to player (use it carefully)",
                        Collections.singletonList(
                                SlashCommandOption.create(
                                        SlashCommandOptionType.STRING,
                                        "name",
                                        "name of the player",
                                        true
                                ))
        ).createGlobal(api).join();
        SlashCommand gameoverCommand = SlashCommand.with("gameover", "Executes gameover event"
        ).createGlobal(api).join();
        SlashCommand loginCommand = SlashCommand.with("login", "Connects your discord and mindustry account!",
                Collections.singletonList(
                        SlashCommandOption.create(
                                SlashCommandOptionType.STRING,
                                "idOrName",
                                "id or name of player",
                                true
                        ))
        ).createGlobal(api).join();
        SlashCommand getInfoCommand = SlashCommand.with("stats", "Gets stats of player",
                Collections.singletonList(
                        SlashCommandOption.create(
                                SlashCommandOptionType.STRING,
                                "idOrName",
                                "Player id or name",
                                true
                        ))
        ).createGlobal(api).join();
        SlashCommand searchCommand = SlashCommand.with("search", "Searchs the players in db",
                Collections.singletonList(
                        SlashCommandOption.create(
                                SlashCommandOptionType.STRING,
                                "name",
                                "Player name",
                                true
                        ))
        ).createGlobal(api).join();

        SlashCommand unbanCommand = SlashCommand.with("unban", "Unbans the player",
                        Collections.singletonList(
                                SlashCommandOption.create(
                                        SlashCommandOptionType.STRING,
                                        "idOrName",
                                        "id or name of the player",
                                        true
                                )
                        )
        ).createGlobal(api).join();
        SlashCommand cmdCommand = SlashCommand.with("js", "Execute js command",
                Collections.singletonList(
                        SlashCommandOption.create(
                                SlashCommandOptionType.STRING,
                                "cmd",
                                "The command you want to execute",
                                true
                        ))
        ).createGlobal(api).join();
    }
    // calling slash command functions once they got used
    private static void addSlashCommandListener(SlashCommandCreateEvent listener) {
        if(!state.is(GameState.State.playing)){
            listener.getSlashCommandInteraction().createImmediateResponder().setContent("Server is not running.").respond();
            return;
        }
        if(listener.getSlashCommandInteraction().getServer().isEmpty()){
            listener.getSlashCommandInteraction().createImmediateResponder().setContent("Cant use commands in DM").respond();
        }
        switch(listener.getSlashCommandInteraction().getCommandName()){
            case "ban" -> {
                if (!isModerator(listener)){
                    return;
                }

                String response;

                String id = listener.getSlashCommandInteraction().getOptionByName("idOrNameOrUUID").get().getStringValue().get();
                String reason = listener.getSlashCommandInteraction().getOptionByName("reason").get().getStringValue().get();
                long time = listener.getSlashCommandInteraction().getOptionByName("time").get().getLongValue().get();
                Date date = new Date();
                long banTime = date.getTime() + TimeUnit.DAYS.toMillis(time);
                String timeUntilUnban = Bundle.formatDuration(Duration.ofDays(time));
                Document user = getDocAnyway(id);
                if (user == null) {
                    response = "Could not find that player.";
                    listener.getSlashCommandInteraction()
                            .createImmediateResponder().setContent(response)
                            .respond();
                    return;
                }
                Player plr = Groups.player.find(p -> p.uuid().equals(user.getString("uuid")));
                if (plr == null) {
                    Log.info("Player is offline, not kicking him");
                } else {
                    plr.con.kick("[red]You have been banned!\n\n" + "[white]Reason: " + reason + "\nDuration: " + timeUntilUnban + " until unban\nIf you think this is a mistake, make sure to appeal ban in our discord: " + discordurl, 0);
                }
                listener.getSlashCommandInteraction()
                        .createImmediateResponder().setContent("Banned: " + user.getString("name"))
                        .respond();

                Call.sendMessage(user.getString("name") + " has been banned for: " + reason);
                MongoDbUpdate(user, Updates.set("lastBan", banTime));
                Bot.banchannel.sendMessage(banEmbed(user, reason, banTime, listener.getInteraction().getUser().getName()));
            }
            case "adminadd" -> {
                if (!isModerator(listener)){
                    return;
                }
                String name = listener.getSlashCommandInteraction().getOptionByName("name").get().getStringValue().get();
                Player player = findPlayerByName(name);
                if (player == null){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("No such player!").respond(); return;
                }
                if (player.admin()){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("Player is already admin!").respond(); return;
                }
                netServer.admins.adminPlayer(String.valueOf(player.uuid()), player.usid());
                listener.getSlashCommandInteraction().createImmediateResponder().setContent("Successfully admin " + player.plainName()).respond();
            }
            case "gameover" -> {
                if (!isModerator(listener)){
                    return;
                }
                Events.fire(new EventType.GameOverEvent(Team.derelict));
                listener.getSlashCommandInteraction().createImmediateResponder().setContent("Gameover executed!").respond();
            }
            case "login" -> {
                String id = listener.getSlashCommandInteraction().getOptionByName("idOrName").get().getStringValue().get();
                Document user = getDocAnyway(id);
                if (user == null){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("This player doesnt exists!").respond();
                    return;
                }
                Player player = Groups.player.find(p -> p.uuid().equals(user.getString("uuid")));
                if (player == null){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("This player is offline!").respond();
                    return;
                }
                loginMenuFunction(listener);
                Call.menu(player.con, loginMenu, "Request", listener.getInteraction().getUser().getName() + " requests to connect your mindustry account", new String[][]{{"Connect"}, {"Cancel"}});
                listener.getSlashCommandInteraction().createImmediateResponder().setContent("req sended!").respond();
            }
            case "stats" -> {
                String id = listener.getSlashCommandInteraction().getOptionByName("idOrName").get().getStringValue().get();
                Document user = getDocAnyway(id);
                if (user == null){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("Could not find that player!").respond();
                    return;
                }
                long playtime = Long.parseLong(String.valueOf(user.getInteger("playtime")));
                String discordId = String.valueOf(user.getLong("discordid"));
                if (discordId.equals("0")){
                    discordId = "none";
                }
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Information")
                        .setColor(Color.RED)
                        .addField("Name", stripColors(user.getString("name")))
                        .addField("ID", String.valueOf(user.getInteger("id")))
                        .addField("Rank", String.valueOf(user.getInteger("rank")))
                        .addField("Playtime",  Bundle.formatDuration(Duration.ofMinutes(playtime)))
                        .addField("Discord (if linked)", "<@" +discordId +">");
                listener.getSlashCommandInteraction().createImmediateResponder().addEmbed(embed).respond();
            }
            case "search" -> {
                String name= listener.getSlashCommandInteraction().getOptionByName("name").flatMap(SlashCommandInteractionOption::getStringValue).orElse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAa");
                StringBuilder list = new StringBuilder();
                Pattern pattern = Pattern.compile(".?" +name + ".?", Pattern.CASE_INSENSITIVE);
                list.append("```Results:\n\n");
                try (MongoCursor<Document> cursor = plrCollection.find(Filters.regex("name", pattern)).limit(25).iterator()) {
                    while (cursor.hasNext()) {
                        Document csr = cursor.next();
                        list.append(csr.get("name")).append("; ID: ").append(csr.get("id")).append("\n");
                    }
                }
                list.append("```");
                listener.getSlashCommandInteraction().createImmediateResponder().setContent(String.valueOf(list)).respond();
            }
            case "unban" -> {
                if (!isModerator(listener)){
                    return;
                }
                String id = listener.getSlashCommandInteraction().getOptionByName("idOrName").get().getStringValue().get();
                Document user = getDocAnyway(id);
                if (user == null){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("Could not find that player!").respond();
                    return;
                }
                if (user.getLong("lastBan") == 0L){
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent("User is not banned!").respond();
                    return;
                }
                MongoDbUpdate(user, Updates.set("lastBan", 0L));
                listener.getSlashCommandInteraction().createImmediateResponder().setContent(user.getString("name") + " has been unbanned!").respond();
            }
            case "js" -> {
                if (!isAdmin(listener)){
                    return;
                }
                String cmd = listener.getSlashCommandInteraction().getOptionByName("cmd").get().getStringValue().get();
                Core.app.post(() -> {
                    String output = mods.getScripts().runConsole(cmd);
                    listener.getSlashCommandInteraction().createImmediateResponder().setContent(output).respond();
                });
            }
          }
        }*/


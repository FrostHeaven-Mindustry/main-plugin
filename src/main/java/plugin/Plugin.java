package plugin;

import arc.ApplicationListener;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.SendChatMessageCallPacket;
import mindustry.net.Packets;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import plugin.commands.handlers.ChatListener;
import plugin.discord.Bot;
import plugin.etc.AntiVpn;
import plugin.models.collections.PlayerData;
import useful.Bundle;

import java.io.File;
import java.io.IOException;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static mindustry.Vars.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static plugin.ConfigJson.discordUrl;
import static plugin.ServersConfig.makeServersConfig;
import static plugin.commands.BanMenu.loadBanMenu;
import static plugin.commands.ConsoleCommands.loadServerCommands;
import static plugin.commands.ChatCommands.*;
import static plugin.commands.history.History.historyPlayers;
import static plugin.commands.history.History.loadHistory;
import static plugin.etc.AntiVpn.loadAntiVPN;
import static plugin.functions.Other.PlaytimeTimer;
import static plugin.functions.Other.kickIfBanned;
import static plugin.functions.Other.welcomeMenu;


public class Plugin extends mindustry.mod.Plugin implements ApplicationListener {
    public static MongoClient mongoClient;
    public static MongoDatabase db;
    public static MongoCollection<PlayerData> players;
    public static JSONObject servers;

    static {
        try {
            servers = makeServersConfig();
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public Plugin() throws IOException, ParseException {
        ConfigJson.read();
        Bot.load();
        ConnectionString string = new ConnectionString(ConfigJson.mongodbUrl);
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));
        mongoClient = MongoClients.create(string);
        db = mongoClient.getDatabase("mindustry").withCodecRegistry(pojoCodecRegistry);
        players = db.getCollection("players", PlayerData.class);
        File dir = new File(Vars.tmpDirectory.absolutePath());
        if (!dir.exists())
            dir.mkdir();
    }

    //  starts once plugin is started
    public void init() {
        loadAntiVPN();
        loadBanMenu();
        loadHistory();
        Log.info("Plugin started!");
        Bundle.load(Plugin.class);

        Events.on(EventType.PlayerJoin.class, event -> {
            Player plr = event.player;
            if (!plr.admin) welcomeMenu(plr);
            plugin.models.wrappers.PlayerData data = new plugin.models.wrappers.PlayerData(event.player);
            kickIfBanned(event.player);
            String joinMessage = data.getJoinMessage().trim();
            Call.sendMessage(joinMessage.replace("@", plr.name()) + " [grey][" + data.getId() + "]");
            Log.info(plr.plainName() + " joined! " + "[" + data.getId() + "]");
        });

        PlaytimeTimer();

        net.handleServer(Packets.Connect.class, (con, connect) -> {
            Events.fire(new EventType.ConnectionEvent(con));

            if (netServer.admins.isIPBanned(connect.addressTCP) || netServer.admins.isSubnetBanned(connect.addressTCP)) {
                con.kick(Packets.KickReason.banned);
            }
            kickIfBanned(con);
            if (AntiVpn.checkAddress(connect.addressTCP))
                con.kick("[orange]You are suspected in using VPN or being a bot! Please, if its not true, report that incident on our discord: " + discordUrl);
        });

        net.handleServer(SendChatMessageCallPacket.class, (con, packet) -> {
            Player player = con.player;
            if (player == null) return;
            if (packet.message == null) return;
            if (player.con.hasConnected && player.isAdded()) {
                String message = packet.message;
                if (message.length() > Vars.maxTextLength) {
                    player.sendMessage("Message too long");
                    return;
                }
                Events.fire(new EventType.PlayerChatEvent(player, message));
                Log.info("[@]: @", player.plainName(), message);
                if (message.startsWith("/")) {
                    ChatListener.handleCommand(player, message.substring(1));
                } else {
                    message = Vars.netServer.admins.filterMessage(player, message.replace("\n", ""));
                    if (message == null) return;
                    Call.sendMessage("[coral][\f" + player.coloredName() + "\f[coral]][white]: " + message, message, player);
                }
            }
        });

        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (isVoting) {
                int votesRequired = (int) Math.ceil((double) Groups.player.size() / 2);
                switch (event.message) {
                    case "y" -> {
                        if (votedPlayer.contains(event.player)) {
                            event.player.sendMessage("You already voted!");
                            return;
                        }
                        votes.getAndAdd(1);
                        votedPlayer.add(event.player);
                        Call.sendMessage(event.player.plainName() + " Voted: " + votes.get() + "/" + votesRequired);
                    }
                    case "n" -> {
                        if (votedPlayer.contains(event.player)) {
                            event.player.sendMessage("You already voted!");
                            return;
                        }
                        votes.getAndAdd(-1);
                        votedPlayer.add(event.player);
                        Call.sendMessage(event.player.plainName() + " Voted: " + votes.get() + "/" + votesRequired);
                    }
                }
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            Player player = event.player;
            historyPlayers.remove(player.uuid());
            plugin.models.wrappers.PlayerData data = new plugin.models.wrappers.PlayerData(player);
            Call.sendMessage(player.name() + "[white] left " + "[grey][" + data.getId() + "]");
            Log.info(player.plainName() + " left " + "[" + data.getId() + "]");
        });
        Vars.netServer.addPacketHandler("MySubtitle", (target, args) -> {
            target.kick("[scarlet]Схемшиз это читы, отключай его!");
        });
        Vars.netServer.addPacketHandler("fooCheck", (target, args) -> {
            target.kick("[scarlet]Фус это читы, удаляй его!");
        });
        Vars.netServer.addPacketHandler("fooTransmission", (target, args) -> {
            target.kick("[scarlet]Удаляй фус!");
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        loadServerCommands(handler);
    }
}

package plugin.commands;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import mindustry.gen.Groups;
import mindustry.gen.Player;

import org.json.simple.parser.ParseException;
import plugin.discord.Bot;
import plugin.etc.Ranks;
import plugin.models.PlayerData;

import java.io.IOException;

import static plugin.Plugin.newCollection;
import static plugin.ServersConfig.resetServersConfig;
import static plugin.functions.MongoDB.*;
import static plugin.utils.FindDocument.getPlayerDataAnyway;
import static plugin.utils.Utilities.findPlayerByID;

public class ConsoleCommands {
    public static void loadServerCommands(CommandHandler handler){
        handler.removeCommand("exit");
        handler.register("exit", "exit the server process", (args) -> {
            Bot.api.disconnect();
            Timer.schedule(()-> {
                System.exit(0);
            }, 1f);
        });
        handler.register("setrank", "<id> <rank>", "Sets rank to player", (args, params) -> {
            PlayerData data = getPlayerDataAnyway(args[0]);
            String rank = args[1];
            if (data == null){
                Log.warn("No such player!");
                return;
            }
            if (Ranks.getRank(rank) == Ranks.Rank.None){
                Log.warn("This rank doesnt exist!");
                return;
            }
            data.rank = rank;
            MongoDbUpdate(data);
            Log.info("Rank has been given!");
            Player player = Groups.player.find(p -> p.uuid().equals(data.uuid));
            if (player == null) {
                return;
            }
        });
        handler.register("setvip", "<id> <true/false>", "Sets vip to player", (args, params) -> {
            PlayerData data = getPlayerDataAnyway(args[0]);
            String isVipString = args[1];
            if (data == null){
                Log.warn("No such player!");
                return;
            }
            if (!(isVipString.equals("true") || isVipString.equals("false"))){
                Log.warn("true or false");
                return;
            }
            boolean isVip = Boolean.parseBoolean(isVipString);
            data.isVip = isVip;
            MongoDbUpdate(data);
            Log.info(data.isVip == true ? "Given Vip." : "Removed Vip.");
            Player player = Groups.player.find(p -> p.uuid().equals(data.uuid));
            if (player == null) {
                return;
            }
        });
        handler.register("check", "Checks mongodb", (args, params) -> {
            MongoDbCheck();
        });
        /*handler.register("transfer", "mongodb transfer", (args) -> {
            try (MongoCursor<PlayerData> cursor = newCollection.find().iterator()) {
                while (cursor.hasNext()) {
                    PlayerData csr = cursor.next();
                    PlayerData data = new PlayerData((String) csr.get("uuid"), csr.getInteger("id"));
                    data.name = csr.getString("name");
                    data.rawName = csr.getString("rawName");
                    data.ip = csr.getString("ip");
                    data.rank = formatRanks(csr.getInteger("rank"));
                    data.playtime = csr.getInteger("playtime");
                    data.achievements = (ArrayList<String>) csr.getList("achievements", String.class);
                    data.joinMessage = csr.getString("joinmessage");
                    data.lastBan = csr.getLong("lastBan");
                    data.discordId = csr.getLong("discordid");
                    try {
                        newCollection.insertOne(data);
                    } catch (Exception e ){
                        Log.debug("Duplicate, ignoring");
                        return;
                    }
                    Log.debug(data.id);
                }
            }
        });*/
         handler.register("reloaddb", "mongodb transfer", (args) -> {
            try (MongoCursor<PlayerData> cursor = newCollection.find().iterator()) {
                while (cursor.hasNext()) {
                    PlayerData csr = cursor.next();
                    MongoDbUpdate(csr);
                }
            }
        });
        handler.register("achcheck", "gives achievements to those who deserves them", (args) -> {
            try (MongoCursor<PlayerData> cursor = newCollection.find(Filters.gte("playtime", 2250)).iterator()) {
                while (cursor.hasNext()) {
                    PlayerData csr = cursor.next();
                    String activeAch = "[lime]Hyper[green]active";
                    if (!csr.achievements.contains(activeAch)) {
                        csr.achievements.add(activeAch);
                        MongoDbUpdate(csr)
                    }
                }
            }
            try (MongoCursor<PlayerData> cursor = newCollection.find(Filters.lte("_id", 150)).iterator()) {
                while (cursor.hasNext()) {
                    PlayerData csr = cursor.next();
                    String activeAch = "[orange]Vete[yellow]ran";
                    if (!csr.achievements.contains(activeAch)) {
                        csr.achievements.add(activeAch);
                        MongoDbUpdate(csr);
                    }
                }
            }
        });
        handler.register("resetserversconfig", "Resets serverConfig.json file", (args) -> {
            try {
                resetServersConfig();
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

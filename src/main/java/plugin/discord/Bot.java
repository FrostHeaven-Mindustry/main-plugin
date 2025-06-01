package plugin.discord;

import arc.Events;
import arc.util.*;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.event.message.MessageCreateEvent;
import plugin.configs.ConfigJson;
import plugin.discord.commands.DiscordCommandRegister;
import plugin.database.wrappers.PlayerData;


@SuppressWarnings("unused")
public class Bot {
    // variables for load function
    public static DiscordApi api;
    public static TextChannel channel;
    public static TextChannel banchannel;
    public static String prefix = ConfigJson.prefix;

    // main bot
    public static void load() {
        api = new DiscordApiBuilder().setToken(ConfigJson.token).addIntents(Intent.GUILDS, Intent.MESSAGE_CONTENT, Intent.GUILD_MESSAGES).login().join();
        api.addMessageCreateListener(Bot::onMessageCreate);
        channel = api.getChannelById(ConfigJson.logChannelId).get().asTextChannel().get();
        banchannel = api.getChannelById(ConfigJson.banLogChannelId).get().asTextChannel().get();
        init();
        PagedEmbed.load();
    }

    // the stuff that logs if bot is started and also some random events
    public static void init() {
        Log.info("Bot started");
        Commands.load();
        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.message.startsWith("/")) {
                return;
            }
            channel.sendMessage(event.player.plainName().replace("`", "") + ": `" + event.message.replace("`", "") + "`");
        });

        Events.on(EventType.PlayerJoin.class, event -> Timer.schedule(() -> {
            PlayerData data = new PlayerData(event.player);
            if (data.isExist()) {
                channel.sendMessage("`" + event.player.plainName().replace("`", "") + " (" + data.getId() + ")" + " joined the server!" + "`");
            }
        }, 0.2f));

        Events.on(EventType.PlayerLeave.class, event -> Timer.schedule(() -> {
            PlayerData data = new PlayerData(event.player);
            if (data.isExist()) {
                channel.sendMessage("`" + event.player.plainName().replace("`", "") + " (" + data.getId() + ")" + " left the server!" + "`");
            }
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
        if (listener.getChannel() == channel && listener.getMessageAuthor().isRegularUser()) {
            Call.sendMessage("[blue][white] > " + listener.getMessageAuthor().getName() + "[white]: " + listener.getMessageContent());
        }
        if (listener.getMessageAuthor().isBotUser()) {
            return;
        }
        DiscordCommandRegister.handleMessage(listener.getMessage());
    }
}

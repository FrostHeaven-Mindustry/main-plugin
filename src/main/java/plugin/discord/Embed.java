package plugin.discord;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import plugin.database.wrappers.PlayerData;
import plugin.Utilities;
import useful.Bundle;

import java.awt.*;
import java.time.Duration;
import java.util.Optional;

public class    Embed {

    public static EmbedBuilder banEmbed(PlayerData data, PlayerData dataModer, String reason, long banTime, String moderator){
        return new EmbedBuilder()
            .setTitle("Ban event")
            .setColor(Color.RED)
            .addField("**ID**", String.valueOf(data.getId()))
            .addField("**Name**", data.getNames().get(data.getNames().size() - 1))
            .addField("**UUID**", data.getUuid())
            .addField("**IP**", data.getIPs().toString())
            .addField("**Reason**", reason)
            .addField("**Expires**", "<t:" + banTime/1000 +":D>")
            .addField("**Moderator**", moderator)
            .addField("**Moderator Id**", String.valueOf(dataModer.getId()));
            
}
    public static EmbedBuilder noRoleEmbed(Optional<Role> role){
        return new EmbedBuilder()
                .setTitle("Not enough permissions!")
                .setColor(Color.RED)
                .setDescription("You should have <@&" + role.get().getId() + "> Role to interact with this command/button!");
    }
    public static EmbedBuilder infoEmbed(PlayerData data) {
        return new EmbedBuilder()
                .setTitle("Player info")
                .setColor(Color.CYAN)
                .addField("**ID**", String.valueOf(data.getId()))
                .addField("**UUID**", data.getUuid())
                .addField("**Names**", Utilities.stringify(data.getNames(), name -> "\n- " + name))
                .addField("**IPs**", Utilities.stringify(data.getIPs(), name -> "\n- " + name))
                .addField("**Playtime**", Bundle.formatDuration(Duration.ofMinutes(data.getPlaytime())));
    }
}

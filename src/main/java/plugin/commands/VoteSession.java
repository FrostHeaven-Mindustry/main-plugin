package plugin.commands;

import arc.struct.ObjectIntMap;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.core.NetServer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Packets;
import plugin.database.wrappers.PlayerData;
import plugin.database.collections.PlayerData;

public class VoteSession {
    private static VoteSession instance;
    public static VoteSession getInstance() {
        return instance;
    }
    public static VoteSession newSession(Player target, Player starter, int req, String reason) {
        if (instance != null) {
            instance.voteTask.cancel();
        }
        instance = new VoteSession(target, starter, req, reason);
        return instance;
    }
    public Player target;
    public int requiredVotes;
    public int currentVotes;
    public Player starter;
    public boolean isAlive;
    public final Timer.Task voteTask;
    public String reason;
    private boolean firstVote = true;
    public ObjectIntMap<Player> votes = new ObjectIntMap<>();
    private VoteSession(Player target, Player starter, int req, String reason) {
        this.currentVotes = 0;
        this.reason = reason;
        this.isAlive = true;
        this.requiredVotes = req;
        this.starter = starter;
        this.target = target;
        voteTask = Timer.schedule(() -> {
            if (currentVotes >= requiredVotes) {
                pass();
                return;
            }
            Call.sendMessage("[lightgray]Vote failed. Not enough votes to kick[orange] " + target.coloredName() + "\f[lightgray].");
            this.isAlive = false;
            instance = null;
        }, NetServer.voteDuration);
    }
    public void vote(Player player, int sign) {
        if (isVoted(player)) return;
        firstVote = false;
        String h;
        switch (sign) {
            case -1 -> {
                currentVotes--;
                votes.put(player, sign);
                h = "[red]against[]";

            }
            case 1 -> {
                currentVotes++;
                votes.put(player, sign);
                h = "[green]for[]";
            }
            default -> {
                return;
            }
        }

        plugin.database.wrappers.PlayerData data = new plugin.database.wrappers.PlayerData(player);

        Call.sendMessage(Strings.format("[lightgray]@\f[lightgray] has voted @ kicking[orange] @\f[lightgray].[accent] (@/@)\n[lightgray]Type[orange] /vote <y/n>[] to agree.",
                player.coloredName()+" [grey]["+data.getId()+"][white]", h, target.coloredName(), currentVotes, requiredVotes));
        checkPassed();
    }

    private boolean isVoted(Player player) {
        var iterator = votes.keys();
        while (iterator.hasNext()) {
            Player entry = iterator.next();
            if(entry.uuid().equals(player.uuid()) || entry.ip().equals(player.ip())) {
                player.sendMessage("[scarlet]You've already voted. Sit down.");
                return true;
            }
        }
        if ((starter.uuid().equals(player.uuid()) || starter.ip().equals(player.ip())) && !firstVote) {
            player.sendMessage("[scarlet]You've already voted. Sit down.");
            return true;
        }
        if (target.uuid().equals(player.uuid()) || target.ip().equals(player.ip())) {
            player.sendMessage("[scarlet]You've already voted. Sit down.");
            return true;
        }
        return false;
    }

    private void checkPassed() {
        if (currentVotes >= requiredVotes) {
            pass();
        }
    }

    public void pass() {
        if (!target.con.hasDisconnected) target.kick(Packets.KickReason.vote, (long) (NetServer.voteDuration * 1000));
        Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] @\f[orange] will be banned from the server for 1 hour.", target.coloredName()));
        voteTask.cancel();
        instance = null;
    }
}

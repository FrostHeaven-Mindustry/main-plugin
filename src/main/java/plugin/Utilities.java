package plugin;
import arc.Events;
import arc.func.Cons;
import arc.func.Func;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Threads;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.javacord.api.entity.permission.Role;
import rhino.Context;
import rhino.NativeJavaObject;
import rhino.Scriptable;
import rhino.Undefined;
import java.util.ArrayList;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

import static plugin.commands.ChatCommands.votedPlayer;
import static plugin.commands.ChatCommands.votes;

public class Utilities {
    private static final Scriptable scope = Reflect.get(Vars.mods.getScripts(), "scope");
    // finds player using their name (without colors)
    public static Player findPlayerByName(String name){
        return Groups.player.find(t-> t.plainName().contains(name));
    }
    public static void voteCanceled(){
        Call.sendMessage("[red]Vote has been canceled!");
        votes.set(0);
        votedPlayer.clear();
    }

    public static boolean checkProxy(String insertedText) {
    try {
        String urlString = "http://ip-api.com/json/" + insertedText + "?fields=proxy,hosting";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) { // 200
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            boolean proxy = jsonResponse.getBoolean("proxy");
            boolean hosting = jsonResponse.getBoolean("hosting");

            return (proxy || hosting);
        } else {
            return false;
        }
    } catch (Exception e) {
        e.printStackTrace();
        return false; // <-- Add this return
    }
    }
    
    public static void voteSuccess(Map map){
        Call.sendMessage("[green]Vote success! Changing map!");
        Vars.maps.setNextMapOverride(map);
        Events.fire(new EventType.GameOverEvent(Team.derelict));
        votes.set(0);
        votedPlayer.clear();
    }
    public static Seq<Map> getMaps(){
        return Vars.maps.customMaps().copy();
    }
    public static <T> String stringify(ArrayList<T> arr, Func<T, String> stringer) {
        if (arr == null || arr.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (T elem: arr) {
            out.append(stringer.get(elem));
        }
        return out.toString();
    }
    public static void runJs(String code, Cons<String> result) {
        Threads.thread(() -> {
            Object out;
            try {
                Object resp = Context.enter().evaluateString(scope, code, "console.js", 1);
                if (resp instanceof NativeJavaObject o) {
                    out = o.unwrap();
                } else if (resp instanceof Undefined) {
                    out = "undefined";
                } else {
                    out = String.valueOf(resp).trim();
                }
            } catch (Exception e) {
                out = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : " ");
            }
            Context.exit();
            result.get(out != null ? out.toString(): "null");
        });
    }
    public static boolean haveRole(java.util.List<org.javacord.api.entity.permission.Role> roles, Set<Long> required) {
        for (Role r: roles) {
            if (required.contains(r.getId())) return true;
        }
        return false;
    }
    public static <T> Seq<Seq<T>> splitBy(Seq<T> arr, int cap) {
        Seq<Seq<T>> res = new Seq<>();
        int start = 0;
        for (int i = 0; i < (arr.size + cap - 1) / cap; i++) {
            int end = Math.min(start + cap, arr.size);
            var s = new Seq<T>();
            for (int j = start; j < end; j++) {
                s.add(arr.get(j));
            }
            res.add(s);
            start = end;
        }
        return res;
    }
}


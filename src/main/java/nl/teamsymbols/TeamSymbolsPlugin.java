
package nl.teamsymbols;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class TeamSymbolsPlugin extends JavaPlugin implements Listener {

    private final Map<String, TeamData> teams = new HashMap<>();
    private final Map<UUID, String> invitations = new HashMap<>();
    private Scoreboard board;

    private static class TeamData {
        String name;
        UUID owner;
        Set<UUID> members = new HashSet<>();
        String color = "geel";
        String symbol = "⚡";
        boolean customSymbol;

        TeamData(String name, UUID owner) {
            this.name = name;
            this.owner = owner;
            members.add(owner);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        board = Bukkit.getScoreboardManager().getMainScoreboard();
        loadTeams();
        Bukkit.getPluginManager().registerEvents(this, this);
        refreshScoreboard();
        getLogger().info("TeamSymbols gestart!");
    }

    @Override
    public void onDisable() {
        saveTeams();
    }

    private NamedTextColor color(String name) {
        return switch (name) {
            case "rood" -> NamedTextColor.RED;
            case "groen" -> NamedTextColor.GREEN;
            case "wit" -> NamedTextColor.WHITE;
            case "oranje" -> NamedTextColor.GOLD;
            case "blauw" -> NamedTextColor.BLUE;
            case "paars" -> NamedTextColor.LIGHT_PURPLE;
            case "zwart" -> NamedTextColor.DARK_GRAY;
            default -> NamedTextColor.YELLOW;
        };
    }

    private ChatColor legacyColor(String name) {
        return switch (name) {
            case "rood" -> ChatColor.RED;
            case "groen" -> ChatColor.GREEN;
            case "wit" -> ChatColor.WHITE;
            case "oranje" -> ChatColor.GOLD;
            case "blauw" -> ChatColor.BLUE;
            case "paars" -> ChatColor.LIGHT_PURPLE;
            case "zwart" -> ChatColor.DARK_GRAY;
            default -> ChatColor.YELLOW;
        };
    }

    private String defaultSymbol(String color) {
        return switch (color) {
            case "rood" -> "♥";
            case "groen" -> "♣";
            case "wit" -> "♛";
            case "oranje" -> "🔥";
            case "blauw" -> "❄";
            case "paars" -> "♠";
            case "zwart" -> "◆";
            default -> "⚡";
        };
    }

    private TeamData getTeam(UUID id) {
        return teams.values().stream()
                .filter(t -> t.members.contains(id))
                .findFirst().orElse(null);
    }

    private TeamData getTeam(String name) {
        return teams.get(name.toLowerCase(Locale.ROOT));
    }

    private boolean leader(Player p, TeamData t) {
        return t.owner.equals(p.getUniqueId());
    }

    private boolean canManage(CommandSender sender, TeamData t) {
        return sender.hasPermission("teamsymbols.admin")
                || (sender instanceof Player p && leader(p, t));
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "[Teams] "
                + ChatColor.GRAY + message);
    }

    private void saveTeams() {
        getConfig().set("teams", null);

        for (TeamData t : teams.values()) {
            String path = "teams." + t.name.toLowerCase(Locale.ROOT);
            getConfig().set(path + ".name", t.name);
            getConfig().set(path + ".owner", t.owner.toString());
            getConfig().set(path + ".color", t.color);
            getConfig().set(path + ".symbol", t.symbol);
            getConfig().set(path + ".custom", t.customSymbol);
            getConfig().set(path + ".members",
                    t.members.stream().map(UUID::toString).toList());
        }
        saveConfig();
    }

    private void loadTeams() {
        var section = getConfig().getConfigurationSection("teams");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "teams." + key;
            try {
                String name = getConfig().getString(path + ".name");
                UUID owner = UUID.fromString(
                        getConfig().getString(path + ".owner"));
                TeamData t = new TeamData(name, owner);
                t.color = getConfig().getString(path + ".color", "geel");
                t.symbol = getConfig().getString(path + ".symbol", "⚡");
                t.customSymbol = getConfig().getBoolean(path + ".custom");

                for (String member :
                        getConfig().getStringList(path + ".members")) {
                    t.members.add(UUID.fromString(member));
                }
                teams.put(name.toLowerCase(Locale.ROOT), t);
            } catch (Exception ex) {
                getLogger().warning("Team kon niet worden geladen: " + key);
            }
        }
    }

    private void refreshScoreboard() {
        for (Team old : new HashSet<>(board.getTeams())) {
            if (old.getName().startsWith("ts_")) {
                old.unregister();
            }
        }

        for (TeamData t : teams.values()) {
            String id = "ts_" + UUID.nameUUIDFromBytes(
                    t.name.toLowerCase(Locale.ROOT)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString().replace("-", "").substring(0, 10);

            Team st = board.registerNewTeam(id);
            st.setPrefix(legacyColor(t.color) + "[" + t.symbol + " "
                    + t.name + "] " + ChatColor.RESET);

            for (UUID member : t.members) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(member);
                if (op.getName() != null) st.addEntry(op.getName());
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, this::refreshScoreboard);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        TeamData t = getTeam(event.getPlayer().getUniqueId());
        if (t == null) return;

        Component prefix = Component.text(
                "[" + t.symbol + " " + t.name + "] ", color(t.color));

        event.renderer((source, sourceName, message, viewer) ->
                prefix.append(sourceName)
                        .append(Component.text(": "))
                        .append(message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "/team create, invite, accept, kick, leave, color, symbol, info, disband");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("accept")) {
            if (!(sender instanceof Player p)) return true;
            String name = invitations.remove(p.getUniqueId());
            TeamData t = name == null ? null : getTeam(name);

            if (t == null) {
                msg(sender, "Je hebt geen geldige uitnodiging.");
                return true;
            }
            if (getTeam(p.getUniqueId()) != null) {
                msg(sender, "Je zit al in een team.");
                return true;
            }

            t.members.add(p.getUniqueId());
            saveTeams();
            refreshScoreboard();
            msg(sender, "Je bent lid van " + t.name + "!");
            return true;
        }

        if (!(sender instanceof Player p)) {
            msg(sender, "Dit commando kan alleen door spelers worden gebruikt.");
            return true;
        }

        TeamData t = getTeam(p.getUniqueId());

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    msg(sender, "Gebruik /team create <naam>");
                    return true;
                }
                if (t != null) {
                    msg(sender, "Je zit al in een team.");
                    return true;
                }
                String name = args[1];
                if (!name.matches("[a-zA-Z0-9_-]{2,16}")) {
                    msg(sender, "Teamnaam: 2-16 letters, cijfers, _ of -.");
                    return true;
                }
                if (getTeam(name) != null) {
                    msg(sender, "Die teamnaam bestaat al.");
                    return true;
                }
                TeamData created = new TeamData(name, p.getUniqueId());
                teams.put(name.toLowerCase(Locale.ROOT), created);
                saveTeams();
                refreshScoreboard();
                msg(sender, "Team " + name + " aangemaakt!");
            }

            case "invite" -> {
                if (t == null || !canManage(sender, t)) {
                    msg(sender, "Alleen de teamleider kan uitnodigen.");
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "Gebruik /team invite <speler>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || getTeam(target.getUniqueId()) != null) {
                    msg(sender, "Speler niet online of zit al in een team.");
                    return true;
                }
                invitations.put(target.getUniqueId(), t.name);
                msg(sender, "Uitnodiging verstuurd.");
                msg(target, "Uitgenodigd voor " + t.name + ". Gebruik /team accept.");
            }

            case "leave" -> {
                if (t == null) {
                    msg(sender, "Je zit niet in een team.");
                    return true;
                }
                if (leader(p, t)) {
                    msg(sender, "De leider moet eerst het team opheffen.");
                    return true;
                }
                t.members.remove(p.getUniqueId());
                saveTeams();
                refreshScoreboard();
                msg(sender, "Je hebt het team verlaten.");
            }

            case "kick" -> {
                if (t == null || !canManage(sender, t) || args.length < 2) {
                    msg(sender, "Gebruik /team kick <speler> als teamleider.");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!t.members.remove(target.getUniqueId())) {
                    msg(sender, "Die speler zit niet in je team.");
                    return true;
                }
                saveTeams();
                refreshScoreboard();
                msg(sender, "Speler verwijderd uit het team.");
            }

            case "color" -> {
                if (t == null || !canManage(sender, t) || args.length < 2) {
                    msg(sender, "Gebruik /team color <geel/rood/groen/wit/oranje/blauw/paars/zwart>.");
                    return true;
                }
                String c = args[1].toLowerCase(Locale.ROOT);
                if (!Set.of("geel", "rood", "groen", "wit",
                        "oranje", "blauw", "paars", "zwart").contains(c)) {
                    msg(sender, "Onbekende kleur.");
                    return true;
                }
                t.color = c;
                if (!t.customSymbol) t.symbol = defaultSymbol(c);
                saveTeams();
                refreshScoreboard();
                msg(sender, "Teamkleur aangepast.");
            }

            case "symbol" -> {
                if (t == null || !canManage(sender, t) || args.length < 2) {
                    msg(sender, "Gebruik /team symbol <symbool>.");
                    return true;
                }
                t.symbol = args[1];
                t.customSymbol = true;
                saveTeams();
                refreshScoreboard();
                msg(sender, "Teamsymbool aangepast.");
            }

            case "info" -> {
                if (t == null) {
                    msg(sender, "Je zit niet in een team.");
                    return true;
                }
                msg(sender, "Team: " + t.name + " | Symbool: " + t.symbol
                        + " | Kleur: " + t.color + " | Leden: " + t.members.size());
            }

            case "disband" -> {
                if (t == null || !canManage(sender, t)) {
                    msg(sender, "Alleen de teamleider kan het team opheffen.");
                    return true;
                }
                teams.remove(t.name.toLowerCase(Locale.ROOT));
                saveTeams();
                refreshScoreboard();
                msg(sender, "Team opgeheven.");
            }

            default -> msg(sender, "Onbekend teamcommando.");
        }
        return true;
    }
}

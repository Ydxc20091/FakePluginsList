package com.fakeplugins.fakepluginslist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakePluginsList extends JavaPlugin {

    private static final String RELOAD_PERMISSION = "fakepluginslist.reload";
    private static final String CATEGORY_PAPER = "paper";
    private static final String CATEGORY_BUKKIT = "bukkit";
    private static final List<String> VERSION_COMMANDS = Arrays.asList("version", "ver");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Map<String, Command> registeredCommands = new HashMap<>();
    private SimpleCommandMap commandMap;
    private Map<String, Command> knownCommands;

    private List<FakePlugin> plugins = new ArrayList<>();
    private List<String> commands = new ArrayList<>();
    private String primaryCommand;
    private String serverHeader;
    private String paperHeader;
    private String bukkitHeader;
    private String realListPermission;
    private boolean showDisabledPlugins;
    private boolean randomOrder;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        resolveCommandMap();
        loadConfigValues();
        registerCommands(commands);
        registerVersionCommands();
        getLogger().info("FakePluginsList enabled.");
    }

    @Override
    public void onDisable() {
        unregisterCommands();
        getLogger().info("FakePluginsList disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("fakepluginslist".equalsIgnoreCase(command.getName())) {
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                if (!sender.hasPermission(RELOAD_PERMISSION)) {
                    sender.sendMessage(parseText("<red>You do not have permission to do that.</red>"));
                    return true;
                }
                reloadPluginConfig(sender);
                return true;
            }
            sender.sendMessage(parseText("<yellow>Usage: /fakepluginslist reload</yellow>"));
            return true;
        }
        return false;
    }

    private void resolveCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap map = (CommandMap) field.get(Bukkit.getServer());
            if (map instanceof SimpleCommandMap) {
                commandMap = (SimpleCommandMap) map;
                knownCommands = commandMap.getKnownCommands();
            } else {
                getLogger().warning("Unsupported CommandMap implementation.");
            }
        } catch (ReflectiveOperationException e) {
            getLogger().warning("Failed to access CommandMap.");
        }
    }

    private void loadConfigValues() {
        reloadConfig();
        commands = new ArrayList<>(getConfig().getStringList("commands"));
        if (commands.isEmpty()) {
            commands.add("plugins");
        }
        primaryCommand = commands.get(0);
        serverHeader = getConfig().getString("server_header", "&f? Server Plugins (&f%count%&f)");
        paperHeader = getConfig().getString("paper_header", "&bPaper Plugins (&f%count%&f):");
        bukkitHeader = getConfig().getString("bukkit_header", "&6Bukkit Plugins (&f%count%&f):");
        realListPermission = getConfig().getString("real_list_permission", "fakepluginslist.real");
        showDisabledPlugins = getConfig().getBoolean("show_disabled_plugins", true);
        randomOrder = getConfig().getBoolean("random_order", true);
        plugins = loadPlugins();
    }

    private List<FakePlugin> loadPlugins() {
        List<FakePlugin> result = new ArrayList<>();
        List<Map<?, ?>> entries = getConfig().getMapList("plugins");
        if (!entries.isEmpty()) {
            for (Map<?, ?> entry : entries) {
                String name = valueAsString(entry.get("name"));
                if (name.isEmpty()) {
                    continue;
                }
                boolean enabled = valueAsBoolean(entry.get("enabled"), true);
                String version = valueAsString(entry.get("version"));
                String description = valueAsString(entry.get("description"));
                List<String> authors = valueAsStringList(entry.get("authors"));
                String category = valueAsString(entry.get("category"));
                result.add(new FakePlugin(name, enabled, version, description, authors, category));
            }
            return result;
        }

        for (String name : getConfig().getStringList("success_plugins")) {
            if (!name.isEmpty()) {
                result.add(new FakePlugin(name, true, "", "", new ArrayList<>(), CATEGORY_BUKKIT));
            }
        }
        for (String name : getConfig().getStringList("failed_plugins")) {
            if (!name.isEmpty()) {
                result.add(new FakePlugin(name, false, "", "", new ArrayList<>(), CATEGORY_BUKKIT));
            }
        }
        return result;
    }

    private String valueAsString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean valueAsBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private List<String> valueAsStringList(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object entry : (List<Object>) value) {
                if (entry != null) {
                    String text = String.valueOf(entry).trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    public void reloadPluginConfig(CommandSender sender) {
        List<String> oldCommands = new ArrayList<>(commands);
        loadConfigValues();
        unregisterCommands(oldCommands);
        registerCommands(commands);
        sender.sendMessage(parseText("<green>FakePluginsList config reloaded.</green>"));
    }

    private void registerCommands(List<String> commandNames) {
        if (commandMap == null) {
            getLogger().warning("CommandMap unavailable. Commands were not registered.");
            return;
        }
        for (String commandName : commandNames) {
            String normalized = commandName.toLowerCase();
            unregisterKnown(normalized);
            FakePluginsCommand command = new FakePluginsCommand(commandName, this);
            commandMap.register(getName().toLowerCase(), command);
            registeredCommands.put(normalized, command);
            if (knownCommands != null && !normalized.contains(":")) {
                knownCommands.put("bukkit:" + normalized, command);
                knownCommands.put("minecraft:" + normalized, command);
            }
            getLogger().info("Registered command /" + commandName);
        }
    }

    private void registerVersionCommands() {
        if (commandMap == null) {
            return;
        }
        for (String commandName : VERSION_COMMANDS) {
            String normalized = commandName.toLowerCase();
            unregisterKnown(normalized);
            FakeVersionCommand command = new FakeVersionCommand(commandName, this);
            commandMap.register(getName().toLowerCase(), command);
            registeredCommands.put(normalized, command);
            if (knownCommands != null && !normalized.contains(":")) {
                knownCommands.put("bukkit:" + normalized, command);
                knownCommands.put("minecraft:" + normalized, command);
            }
            getLogger().info("Registered command /" + commandName);
        }
    }

    private void unregisterCommands() {
        unregisterCommands(new ArrayList<>(registeredCommands.keySet()));
    }

    private void unregisterCommands(List<String> commandNames) {
        if (knownCommands == null) {
            registeredCommands.clear();
            return;
        }
        for (String commandName : commandNames) {
            String normalized = commandName.toLowerCase();
            Command removed = registeredCommands.remove(normalized);
            if (removed != null) {
                knownCommands.values().removeIf(cmd -> cmd == removed);
            }
            unregisterKnown(normalized);
        }
    }

    private void unregisterKnown(String name) {
        if (knownCommands == null) {
            return;
        }
        knownCommands.remove(name);
        knownCommands.remove("bukkit:" + name);
        knownCommands.remove("minecraft:" + name);
        knownCommands.remove(getName().toLowerCase() + ":" + name);
    }

    public void sendPluginsList(CommandSender sender) {
        if (realListPermission != null && !realListPermission.isEmpty() && sender.hasPermission(realListPermission)) {
            sendRealPluginsList(sender);
            return;
        }
        List<FakePlugin> paper = new ArrayList<>();
        List<FakePlugin> bukkit = new ArrayList<>();
        for (FakePlugin plugin : plugins) {
            if (!plugin.enabled && !showDisabledPlugins) {
                continue;
            }
            if (CATEGORY_PAPER.equalsIgnoreCase(plugin.category)) {
                paper.add(plugin);
            } else {
                bukkit.add(plugin);
            }
        }

        if (randomOrder) {
            Collections.shuffle(paper);
            Collections.shuffle(bukkit);
        }

        List<FakePlugin> all = new ArrayList<>();
        all.addAll(paper);
        all.addAll(bukkit);

        Component output = Component.empty()
                .append(parseText(replaceCounts(serverHeader, all)))
                .append(Component.newline())
                .append(parseText(replaceCounts(paperHeader, paper)))
                .append(Component.newline())
                .append(buildPluginsLine(paper))
                .append(Component.newline())
                .append(parseText(replaceCounts(bukkitHeader, bukkit)))
                .append(Component.newline())
                .append(buildPluginsLine(bukkit));

        sender.sendMessage(output);
    }

    private void sendRealPluginsList(CommandSender sender) {
        org.bukkit.plugin.Plugin[] loaded = Bukkit.getPluginManager().getPlugins();
        List<String> names = new ArrayList<>();
        for (org.bukkit.plugin.Plugin plugin : loaded) {
            if (plugin == null) {
                continue;
            }
            String name = plugin.getDescription().getName();
            if (name != null && !name.isEmpty()) {
                names.add((plugin.isEnabled() ? "&a" : "&c") + name);
            }
        }
        if (randomOrder) {
            Collections.shuffle(names);
        }
        String header = replaceCounts(serverHeader, toFakeList(names));
        Component output = parseText(header + "\n &8- " + String.join("&f, ", names));
        sender.sendMessage(output);
    }

    private List<FakePlugin> toFakeList(List<String> names) {
        List<FakePlugin> list = new ArrayList<>();
        for (String name : names) {
            list.add(new FakePlugin(name, true, "", "", new ArrayList<>(), CATEGORY_BUKKIT));
        }
        return list;
    }

    private String replaceCounts(String text, List<FakePlugin> list) {
        return text.replace("%count%", String.valueOf(list.size()))
                .replace("%enabled_count%", String.valueOf(countEnabled(list)))
                .replace("%disabled_count%", String.valueOf(countDisabled(list)));
    }

    private int countEnabled(List<FakePlugin> list) {
        int count = 0;
        for (FakePlugin plugin : list) {
            if (plugin.enabled) {
                count++;
            }
        }
        return count;
    }

    private int countDisabled(List<FakePlugin> list) {
        int count = 0;
        for (FakePlugin plugin : list) {
            if (!plugin.enabled) {
                count++;
            }
        }
        return count;
    }

    private Component buildHoverText(FakePlugin plugin) {
        Component version = parseText(plugin.version.isEmpty()
                ? "&fVersion: &7Unknown"
                : "&fVersion: &a" + plugin.version);
        Component description = parseText(plugin.description.isEmpty()
                ? "&fDescription: &7No description"
                : "&fDescription: &a" + plugin.description);
        Component author = parseText(plugin.authors.isEmpty()
                ? "&fAuthor: &7Unknown"
                : "&fAuthor: &a" + String.join(", ", plugin.authors));

        return Component.empty()
                .append(version)
                .append(Component.newline())
                .append(description)
                .append(Component.newline())
                .append(author);
    }

    private Component buildInfoText(FakePlugin plugin) {
        return Component.empty()
                .append(parseText("&6" + plugin.name))
                .append(Component.newline())
                .append(buildHoverText(plugin));
    }

    private void sendPluginInfo(CommandSender sender, String name) {
        if (name == null || name.isEmpty()) {
            sender.sendMessage(parseText("<red>Please specify a plugin name.</red>"));
            return;
        }
        FakePlugin target = null;
        for (FakePlugin plugin : plugins) {
            if (plugin.name.equalsIgnoreCase(name)) {
                target = plugin;
                break;
            }
        }
        if (target == null) {
            sender.sendMessage(parseText("<red>Plugin not found.</red>"));
            return;
        }
        sender.sendMessage(buildInfoText(target));
    }

    private String joinArgs(String[] args, int startIndex) {
        if (args == null || args.length <= startIndex) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private List<FakePlugin> getVisiblePlugins() {
        List<FakePlugin> visible = new ArrayList<>();
        for (FakePlugin plugin : plugins) {
            if (!plugin.enabled && !showDisabledPlugins) {
                continue;
            }
            visible.add(plugin);
        }
        if (randomOrder) {
            Collections.shuffle(visible);
        }
        return visible;
    }

    private FakePlugin findFakePluginByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (FakePlugin plugin : plugins) {
            if (plugin.name.equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }

    private Component buildVersionPluginsLine(List<FakePlugin> list) {
        Component line = parseText("&fPlugins (" + list.size() + "): ");
        for (int i = 0; i < list.size(); i++) {
            FakePlugin plugin = list.get(i);
            Component pluginComponent = Component.text(plugin.name,
                    plugin.enabled ? NamedTextColor.GREEN : NamedTextColor.RED);
            line = line.append(pluginComponent);
            if (i < list.size() - 1) {
                line = line.append(parseText("&f, "));
            }
        }
        return line;
    }

    private Component buildVersionPluginsLineForReal(org.bukkit.plugin.Plugin[] loaded) {
        List<org.bukkit.plugin.Plugin> list = new ArrayList<>();
        for (org.bukkit.plugin.Plugin plugin : loaded) {
            if (plugin != null) {
                list.add(plugin);
            }
        }
        Component line = parseText("&fPlugins (" + list.size() + "): ");
        for (int i = 0; i < list.size(); i++) {
            org.bukkit.plugin.Plugin plugin = list.get(i);
            String name = plugin.getDescription().getName();
            Component pluginComponent = Component.text(name,
                    plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED);
            line = line.append(pluginComponent);
            if (i < list.size() - 1) {
                line = line.append(parseText("&f, "));
            }
        }
        return line;
    }

    private void sendVersionInfo(CommandSender sender) {
        String headerText = "&fThis server is running " + Bukkit.getName()
                + " version " + Bukkit.getVersion()
                + " (Implementing API version " + Bukkit.getBukkitVersion() + ")";
        Component header = parseText(headerText);
        if (realListPermission != null && !realListPermission.isEmpty() && sender.hasPermission(realListPermission)) {
            Component line = buildVersionPluginsLineForReal(Bukkit.getPluginManager().getPlugins());
            sender.sendMessage(Component.empty().append(header).append(Component.newline()).append(line));
            return;
        }
        List<FakePlugin> visible = getVisiblePlugins();
        Component line = buildVersionPluginsLine(visible);
        sender.sendMessage(Component.empty().append(header).append(Component.newline()).append(line));
    }

    private void sendVersionPluginInfo(CommandSender sender, String name) {
        if (name == null || name.isEmpty()) {
            sender.sendMessage(parseText("<red>Please specify a plugin name.</red>"));
            return;
        }
        if (realListPermission != null && !realListPermission.isEmpty() && sender.hasPermission(realListPermission)) {
            org.bukkit.plugin.Plugin real = Bukkit.getPluginManager().getPlugin(name);
            if (real != null) {
                String version = real.getDescription().getVersion();
                String output = "&a" + real.getDescription().getName() + " &fversion &a"
                        + (version == null || version.isEmpty() ? "Unknown" : version);
                sender.sendMessage(parseText(output));
                return;
            }
        }
        FakePlugin target = findFakePluginByName(name);
        if (target == null) {
            sender.sendMessage(parseText("<red>This server is not running any plugin by that name.</red>"));
            return;
        }
        String version = target.version.isEmpty() ? "Unknown" : target.version;
        sender.sendMessage(parseText("&a" + target.name + " &fversion &a" + version));
    }

    private Component parseText(String text) {
        if (text == null) {
            return Component.empty();
        }
        if (text.indexOf('<') >= 0) {
            return MINI.deserialize(text);
        }
        return LEGACY.deserialize(text);
    }

    private Component buildPluginsLine(List<FakePlugin> list) {
        if (list.isEmpty()) {
            return parseText(" &8-");
        }
        Component line = parseText(" &8- ");
        for (int i = 0; i < list.size(); i++) {
            FakePlugin plugin = list.get(i);
            Component pluginComponent = Component.text(plugin.name,
                    plugin.enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(buildHoverText(plugin)))
                    .clickEvent(ClickEvent.runCommand("/" + primaryCommand + " info " + plugin.name));
            line = line.append(pluginComponent);
            if (i < list.size() - 1) {
                line = line.append(parseText("&f, "));
            }
        }
        return line;
    }

    private static final class FakePlugin {
        private final String name;
        private final boolean enabled;
        private final String version;
        private final String description;
        private final List<String> authors;
        private final String category;

        private FakePlugin(String name, boolean enabled, String version, String description, List<String> authors, String category) {
            this.name = name;
            this.enabled = enabled;
            this.version = version;
            this.description = description;
            this.authors = authors;
            this.category = category == null || category.isEmpty() ? CATEGORY_BUKKIT : category;
        }
    }

    private static final class FakePluginsCommand extends Command {

        private final FakePluginsList plugin;

        private FakePluginsCommand(String name, FakePluginsList plugin) {
            super(name);
            this.plugin = plugin;
            setDescription("Fake plugins list.");
            setUsage("/" + name);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                if (!sender.hasPermission(RELOAD_PERMISSION)) {
                    sender.sendMessage(plugin.parseText("<red>You do not have permission to do that.</red>"));
                    return true;
                }
                plugin.reloadPluginConfig(sender);
                return true;
            }
            if (args.length > 0 && "info".equalsIgnoreCase(args[0])) {
                String name = plugin.joinArgs(args, 1);
                plugin.sendPluginInfo(sender, name);
                return true;
            }
            plugin.sendPluginsList(sender);
            return true;
        }
    }

    private static final class FakeVersionCommand extends Command {

        private final FakePluginsList plugin;

        private FakeVersionCommand(String name, FakePluginsList plugin) {
            super(name);
            this.plugin = plugin;
            setDescription("Fake version command.");
            setUsage("/" + name + " [plugin]");
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (args.length > 0) {
                String name = plugin.joinArgs(args, 0);
                plugin.sendVersionPluginInfo(sender, name);
                return true;
            }
            plugin.sendVersionInfo(sender);
            return true;
        }
    }
}

# FakePluginsList

Spoof the `/plugins` list and `/version` output with a configurable set of fake plugins.
Supports hover details, click-to-show info, and optional access to the real list via permission.

## Features
- Fake `/plugins` list (aliases supported).
- Click a plugin name to show version/description/author.
- Fake `/version` and `/ver` output, including a fake plugins list.
- `/version <plugin>` outputs a vanilla-style response for fake plugins.
- Optional permission to see the real plugins list and real `/version` plugin info.
- Toggle disabled plugins, random order, and categories.

## Commands
- `/plugins` (aliases from config) - Show fake plugins list.
- `/plugins info <plugin>` - Show details for a fake plugin.
- `/version` or `/ver` - Show server version and fake plugins list.
- `/version <plugin>` - Show a vanilla-style version line for a fake plugin.
- `/fakepluginslist reload` - Reload configuration.

## Permissions
- `fakepluginslist.reload` - Allow `/fakepluginslist reload` (default: op).
- `fakepluginslist.real` - Show the real plugins list and real `/version <plugin>` info (default: op).

## Configuration
File: `src/main/resources/config.yml`

```yaml
commands:
  - "plugins"
  - "pl"

show_disabled_plugins: true
real_list_permission: "fakepluginslist.real"
random_order: true

server_header: "&bServer Plugins (%count%):"
paper_header: "&bPaper Plugins (%count%):"
bukkit_header: "&6Bukkit Plugins (%count%):"

plugins:
  - name: "ItemAdder"
    category: "paper"
    enabled: true
    version: "4.0.1"
    description: "Custom items plugin with high CPU and memory usage"
    authors:
      - "LoneDev"
```

## Output Examples

Fake `/plugins` list (click a name to show details):
```
Server Plugins (3):
Paper Plugins (1):
 - ItemAdder
Bukkit Plugins (2):
 - MythicMobs, Citizens
```

Fake `/version`:
```
This server is running Paper version 1.20.4 (Implementing API version 1.20.4-R0.1-SNAPSHOT)
Plugins (3): ItemAdder, MythicMobs, Citizens
```

Fake `/version <plugin>`:
```
ItemAdder version 4.0.1
```

## Build
```bash
mvn clean package
```
The jar will be in `target/`.

## Notes
- If the sender has `fakepluginslist.real`, the plugin shows the real `/plugins` list and real `/version <plugin>` output.
- Fake plugin entries are read from `config.yml`.

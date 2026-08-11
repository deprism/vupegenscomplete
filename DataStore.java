package dev.vupe.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.vupe.core.VupeCore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DataStore {
    private final VupeCore plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean serverDirty = new AtomicBoolean(false);
    private ServerData server = new ServerData();

    public DataStore(VupeCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            Files.createDirectories(playerDir());
            Path serverFile = serverFile();
            if (Files.exists(serverFile)) {
                server = gson.fromJson(Files.readString(serverFile), ServerData.class);
                if (server == null) server = new ServerData();
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed loading server data: " + ex.getMessage());
            server = new ServerData();
        }
    }

    public PlayerData player(UUID uuid) {
        return players.computeIfAbsent(uuid, this::loadPlayer);
    }

    public Collection<PlayerData> loadedPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public ServerData server() {
        return server;
    }

    public void markDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    public void markServerDirty() {
        serverDirty.set(true);
    }

    public void unload(UUID uuid) {
        PlayerData data = players.get(uuid);
        if (data != null) {
            savePlayer(uuid, data);
            players.remove(uuid);
            dirtyPlayers.remove(uuid);
        }
    }

    public void saveDirty() {
        // Called from the primary thread. Snapshot mutable Bukkit/plugin state here,
        // then perform only file I/O off-thread.
        Map<Path, String> writes = new LinkedHashMap<>();

        for (UUID uuid : new HashSet<>(dirtyPlayers)) {
            PlayerData data = players.get(uuid);
            if (data == null) {
                dirtyPlayers.remove(uuid);
                continue;
            }
            try {
                writes.put(playerDir().resolve(uuid + ".json"), gson.toJson(data));
                dirtyPlayers.remove(uuid);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not snapshot player " + uuid + ": " + ex.getMessage());
            }
        }

        if (serverDirty.compareAndSet(true, false)) {
            try {
                writes.put(serverFile(), gson.toJson(server));
            } catch (Exception ex) {
                serverDirty.set(true);
                plugin.getLogger().warning("Could not snapshot server data: " + ex.getMessage());
            }
        }

        if (writes.isEmpty()) return;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<Path, String> entry : writes.entrySet()) {
                if (!atomicWrite(entry.getKey(), entry.getValue())) {
                    String file = entry.getKey().getFileName().toString();
                    if (file.equals("server.json")) {
                        serverDirty.set(true);
                    } else if (file.endsWith(".json")) {
                        try {
                            dirtyPlayers.add(UUID.fromString(file.substring(0, file.length() - 5)));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        });
    }

    public void saveAllSync() {
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            savePlayer(entry.getKey(), entry.getValue());
        }
        saveServer();
    }

    private PlayerData loadPlayer(UUID uuid) {
        Path file = playerDir().resolve(uuid + ".json");
        if (!Files.exists(file)) {
            PlayerData data = new PlayerData(uuid);
            data.money = plugin.configs().get("economy").getDouble("economy.starting-money", 5000);
            data.crystals = plugin.configs().get("economy").getLong("economy.starting-crystals", 0);
            data.gold = plugin.configs().get("economy").getLong("economy.starting-gold", 0);
            data.generatorSlots = plugin.configs().get("generators").getInt("generators.per-player-default-slots", 25);
            return data;
        }

        try {
            PlayerData data = gson.fromJson(Files.readString(file), PlayerData.class);
            if (data == null) data = new PlayerData(uuid);
            data.uuid = uuid;
            return data;
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load player " + uuid + ": " + ex.getMessage());
            return new PlayerData(uuid);
        }
    }

    private boolean savePlayer(UUID uuid, PlayerData data) {
        return atomicWrite(playerDir().resolve(uuid + ".json"), gson.toJson(data));
    }

    private boolean saveServer() {
        return atomicWrite(serverFile(), gson.toJson(server));
    }

    private boolean atomicWrite(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Data save failed for " + file + ": " + ex.getMessage());
            return false;
        }
    }

    private Path playerDir() {
        String configured = plugin.configs().main().getString("storage.player-folder", "data/players");
        return plugin.getDataFolder().toPath().resolve(configured);
    }

    private Path serverFile() {
        String configured = plugin.configs().main().getString("storage.server-file", "data/server.json");
        return plugin.getDataFolder().toPath().resolve(configured);
    }
}

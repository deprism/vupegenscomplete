package dev.vupe.core.data;

import java.util.*;

public final class PlayerData {
    public UUID uuid;
    public String lastName = "";
    public double money = 0;
    public boolean economyInitialized = false;
    public long crystals = 0;
    public long gold = 0;

    public String donorRank = "";
    public int donorGrantedSlots = 0;
    public double donorGrantedSellBonus = 0.0;
    public int progressionGrantedSlots = 0;
    public double progressionGrantedSellBonus = 0.0;
    public boolean started = false;
    public String progressionRank = "starter";
    public int level = 1;
    public int prestige = 0;
    public long xp = 0;
    public Set<String> claimedLevelRewards = new HashSet<>();

    public int generatorSlots = 25;
    public double sellMultiplierBonus = 0.0;
    public boolean autosellEnabled = false;
    public double sellBoosterMultiplier = 1.0;
    public long sellBoosterUntil = 0;
    public double crystalBoosterMultiplier = 1.0;
    public long crystalBoosterUntil = 0;
    public Map<String, Integer> crateKeys = new HashMap<>();
    public List<String> pendingCrateRewards = new ArrayList<>();
    public List<String> auctionClaims = new ArrayList<>();
    public List<String> auctionExpiredItems = new ArrayList<>();
    public Map<String, Integer> virtualGeneratorStorage = new HashMap<>();

    public Map<String, String> homes = new HashMap<>();
    public String backLocation = "";
    public Map<Integer, String> vaults = new HashMap<>();

    public long playtimeSeconds = 0;
    public long lastJoinEpoch = 0;
    public long lastActiveEpoch = 0;
    public boolean afk = false;
    public boolean vanished = false;
    public String nickname = "";
    public boolean captchaVerified = false;
    public String lastIpHash = "";
    public String discordId = "";
    public Set<String> tags = new HashSet<>();
    public Set<String> ignoredPlayers = new HashSet<>();
    public boolean socialSpy = false;
    public String activeTag = "";
    public String chatColor = "gray";

    public String teamId = "";
    public String plotId = "";
    public String islandId = "";

    public long miningBlocks = 0;
    public double miningValue = 0;
    public int backpackLevel = 1;
    public int fishingXp = 0;
    public int fishingLevel = 1;
    public int coolerSlots = 9;
    public Map<String, Integer> fishingEnchants = new HashMap<>();
    public Map<String, Integer> miningEnchants = new HashMap<>();
    public Map<String, Integer> farmingEnchants = new HashMap<>();
    public boolean ownsFarmerHoe = false;
    public int hopperLimit = 25;
    public int chestLimit = 50;
    public Set<String> ownedHoppers = new HashSet<>();
    public Set<String> ownedChests = new HashSet<>();
    public List<String> cooler = new ArrayList<>();

    public Map<String, Long> cooldowns = new HashMap<>();
    public Map<String, Double> missionProgress = new HashMap<>();
    public Set<String> completedMissions = new HashSet<>();
    public long missionResetEpoch = 0;

    public long kills = 0;
    public long deaths = 0;
    public double bounty = 0;
    public Map<String, Boolean> options = new HashMap<>();

    public PlayerData() {}

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public int keyCount(String crate) {
        return crateKeys.getOrDefault(crate.toLowerCase(Locale.ROOT), 0);
    }

    public void addKeys(String crate, int amount) {
        crateKeys.merge(crate.toLowerCase(Locale.ROOT), amount, Integer::sum);
        if (crateKeys.get(crate.toLowerCase(Locale.ROOT)) <= 0) {
            crateKeys.remove(crate.toLowerCase(Locale.ROOT));
        }
    }
}

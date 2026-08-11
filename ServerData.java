package dev.vupe.core.data;

import java.util.*;

public final class ServerData {
    public Map<String, String> locations = new HashMap<>();
    public Map<String, String> npcLocations = new HashMap<>();
    public Map<String, String> crateLocations = new HashMap<>();
    public Map<String, String> leaderboardLocations = new HashMap<>();
    public Map<String, String> supplyLocations = new HashMap<>();

    public Map<String, GeneratorRecord> generators = new HashMap<>();
    public Map<String, BreakGeneratorRecord> breakGenerators = new HashMap<>();
    public Map<String, MinionRecord> minions = new HashMap<>();
    public Map<String, AutoSellChestRecord> autosellChests = new HashMap<>();
    public Map<String, TeamRecord> teams = new HashMap<>();
    public Map<String, AuctionRecord> auctions = new HashMap<>();
    public Map<String, CoinflipRecord> coinflips = new HashMap<>();
    public Map<String, PunishmentRecord> punishments = new HashMap<>();
    public Map<String, ReportRecord> reports = new HashMap<>();
    public Map<String, Set<String>> ipAccounts = new HashMap<>();

    public int nextAuctionId = 1;

    public boolean starterBoostEnabled = true;
    public long season = 1;
    public int votePartyProgress = 0;
    public int globalChatCooldownSeconds = 0;
    public double globalSellBoosterMultiplier = 1.0;
    public long globalSellBoosterUntil = 0;
    public double globalCrystalBoosterMultiplier = 1.0;
    public long globalCrystalBoosterUntil = 0;
    public Set<String> knownPlayers = new HashSet<>();




    public static final class AutoSellChestRecord {
        public String id;
        public String owner;
        public String location;
        public int tier = 1;
        public double earnings = 0;
        public long itemsSold = 0;
        public long lastSellAt = 0;
        public boolean hologram = true;
        public String hologramUuid = "";
    }

    public static final class MinionRecord {
        public String id;
        public String owner;
        public String type;
        public String location;
        public long stored;
        public long createdAt;
        public String entityUuid;
    }

    public static final class BreakGeneratorRecord {
        public String owner;
        public String tier;
        public String location;
    }

    public static final class GeneratorRecord {
        public String owner;
        public String type;
        public String location;
        public long placedAt;
    }


    public static final class TeamRecord {
        public String id;
        public String name;
        public String owner;
        public Set<String> members = new HashSet<>();
        public Set<String> invites = new HashSet<>();
        public long createdAt;
    }

    public static final class AuctionRecord {
        public String id;
        public String seller;
        public String itemBase64;

        // `price` is retained for automatic migration from VupeCore 1.x/2.0 data.
        public double price;
        public double startingBid;
        public double topBid;
        public String bidder = "";
        public int bids = 0;

        public long createdAt;
        public long expiresAt;
        public String status = "ACTIVE";
        public long lastBidAt = 0;
    }

    public static final class CoinflipRecord {
        public String id;
        public String creator;
        public double amount;
        public long createdAt;
    }


    public static final class ReportRecord {
        public String id;
        public String reporter;
        public String target;
        public String reason;
        public long createdAt;
        public String status = "OPEN";
        public String handledBy = "";
    }

    public static final class PunishmentRecord {
        public String id;
        public String target;
        public String actor;
        public String type;
        public String reason;
        public long createdAt;
        public long expiresAt;
        public boolean active;
    }
}

package dev.vupe.core.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private TimeUtil() {}

    public static long parseMillis(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("permanent")) return 0;
        Matcher matcher = PART.matcher(input.replace(" ", ""));
        long millis = 0;
        while (matcher.find()) {
            long n = Long.parseLong(matcher.group(1));
            millis += switch (matcher.group(2).toLowerCase()) {
                case "s" -> n * 1000L;
                case "m" -> n * 60_000L;
                case "h" -> n * 3_600_000L;
                case "d" -> n * 86_400_000L;
                case "w" -> n * 604_800_000L;
                default -> 0L;
            };
        }
        return millis;
    }

    public static String pretty(long millis) {
        if (millis <= 0) return "0s";
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long minutes = d.minusDays(days).minusHours(hours).toMinutes();
        long seconds = d.minusDays(days).minusHours(hours).minusMinutes(minutes).toSeconds();
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (seconds > 0 || out.isEmpty()) out.append(seconds).append("s");
        return out.toString().trim();
    }
}

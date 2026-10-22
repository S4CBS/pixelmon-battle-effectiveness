package com.pixelmoneffectiveness.client.telegram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public class TelegramBotClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("PixelmonTelegramBot");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Pixelmon-Telegram-Sender");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentLinkedQueue<String> OUTBOX = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, Long> NOTIFIED_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> NOTIFIED_POSITIONS = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRE_MS = 20L * 60L * 1000L; // 20 minutes

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    static {
        // Poll outbox every 1 second to stay well within Telegram's 1 msg/sec per chat rate limit
        EXECUTOR.scheduleWithFixedDelay(TelegramBotClient::processOutbox, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    public static boolean isConfigured() {
        if (EffectivenessConfig.CONFIG == null) return false;
        if (!EffectivenessConfig.CONFIG.telegramEnabled.get()) return false;
        String token = EffectivenessConfig.CONFIG.telegramBotToken.get().trim();
        String chatId = EffectivenessConfig.CONFIG.telegramChatId.get().trim();
        return !token.isEmpty() && !chatId.isEmpty();
    }

    public static boolean isEntityNotified(UUID uuid) {
        if (uuid == null) return false;
        cleanExpiredCaches();
        Long time = NOTIFIED_ENTITIES.get(uuid);
        return time != null && (System.currentTimeMillis() - time < CACHE_EXPIRE_MS);
    }

    public static void markEntityNotified(UUID uuid) {
        if (uuid == null) return;
        NOTIFIED_ENTITIES.put(uuid, System.currentTimeMillis());
    }

    public static boolean isPositionNotified(BlockPos pos) {
        if (pos == null) return false;
        cleanExpiredCaches();
        Long time = NOTIFIED_POSITIONS.get(pos);
        return time != null && (System.currentTimeMillis() - time < CACHE_EXPIRE_MS);
    }

    public static void markPositionNotified(BlockPos pos) {
        if (pos == null) return;
        NOTIFIED_POSITIONS.put(pos, System.currentTimeMillis());
    }

    private static void cleanExpiredCaches() {
        long now = System.currentTimeMillis();
        NOTIFIED_ENTITIES.entrySet().removeIf(e -> (now - e.getValue() > CACHE_EXPIRE_MS));
        NOTIFIED_POSITIONS.entrySet().removeIf(e -> (now - e.getValue() > CACHE_EXPIRE_MS));
    }

    public static boolean isPokemonTracked(String speciesName) {
        if (speciesName == null || speciesName.trim().isEmpty()) return false;
        if (EffectivenessConfig.CONFIG == null) return false;
        String filter = EffectivenessConfig.CONFIG.telegramPokemonFilter.get().trim();
        if (filter.isEmpty()) return false;

        String[] tokens = filter.split("[,;]+");
        String lower = speciesName.toLowerCase(Locale.ROOT);
        for (String t : tokens) {
            String token = t.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty() && token.equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getTrackedPokemonSet() {
        Set<String> set = new LinkedHashSet<>();
        if (EffectivenessConfig.CONFIG != null) {
            String filter = EffectivenessConfig.CONFIG.telegramPokemonFilter.get().trim();
            if (!filter.isEmpty()) {
                for (String s : filter.split("[,;]+")) {
                    String t = s.trim();
                    if (!t.isEmpty()) {
                        set.add(t);
                    }
                }
            }
        }
        return set;
    }

    public static void setTrackedPokemonSet(Set<String> names) {
        if (EffectivenessConfig.CONFIG != null) {
            String joined = String.join(", ", names);
            EffectivenessConfig.CONFIG.telegramPokemonFilter.set(joined);
            EffectivenessConfig.SPEC.save();
        }
    }

    public static boolean toggleEnabled() {
        if (EffectivenessConfig.CONFIG == null) return false;
        boolean now = !EffectivenessConfig.CONFIG.telegramEnabled.get();
        EffectivenessConfig.CONFIG.telegramEnabled.set(now);
        EffectivenessConfig.SPEC.save();
        return now;
    }

    public static void sendNotification(String htmlMessage) {
        if (!isConfigured()) return;
        OUTBOX.offer(htmlMessage);
    }

    private static void processOutbox() {
        String msg = OUTBOX.poll();
        if (msg == null) return;

        if (EffectivenessConfig.CONFIG == null) return;
        String token = EffectivenessConfig.CONFIG.telegramBotToken.get().trim();
        String chatId = EffectivenessConfig.CONFIG.telegramChatId.get().trim();
        if (token.isEmpty() || chatId.isEmpty()) return;

        try {
            sendHttpRequest(token, chatId, msg);
        } catch (Throwable t) {
            LOGGER.error("Failed to send notification to Telegram: {}", t.getMessage());
        }
    }

    public static void sendTestNotification(String token, String chatId, BiConsumer<Boolean, String> callback) {
        if (token == null || token.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
            if (callback != null) callback.accept(false, "Токен или Chat ID не заполнены");
            return;
        }

        String nowTime = LocalTime.now().format(TIME_FMT);
        String testHtml = "🔔 <b>[Pixelmon] Тестовое уведомление!</b>\n\n"
                + "✅ <b>Telegram бот успешно подключен к Pixelmon!</b>\n"
                + "▫️ <b>Время теста:</b> <code>" + nowTime + "</code>\n"
                + "▫️ Бот готов отправлять спавны Шайни, Легендарных покемонов, Ультра-боссов, Тера-рейдов и сундуков с точными координатами!";

        CompletableFuture.runAsync(() -> {
            try {
                String responseBody = sendHttpRequest(token.trim(), chatId.trim(), testHtml);
                if (callback != null) callback.accept(true, "Успешно отправлено!");
            } catch (TelegramApiException e) {
                if (callback != null) callback.accept(false, e.getMessage());
            } catch (Throwable t) {
                if (callback != null) callback.accept(false, "Ошибка сети: " + t.getMessage());
            }
        });
    }

    private static String sendHttpRequest(String token, String chatId, String htmlText) throws Exception {
        String form = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(htmlText, StandardCharsets.UTF_8)
                + "&parse_mode=HTML"
                + "&disable_web_page_preview=true";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body();

        if (response.statusCode() != 200) {
            String errorMsg = "HTTP " + response.statusCode();
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("description")) {
                    errorMsg += ": " + json.get("description").getAsString();
                }
            } catch (Throwable ignored) {
                errorMsg += ": " + body;
            }
            throw new TelegramApiException(errorMsg);
        }

        return body;
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public static String formatLocationBlock(BlockPos pos, Level level) {
        int x = pos != null ? pos.getX() : 0;
        int y = pos != null ? pos.getY() : 0;
        int z = pos != null ? pos.getZ() : 0;

        String dim = level != null ? level.dimension().location().toString() : "minecraft:overworld";
        String biomeName = "Unknown";
        try {
            if (level != null && pos != null) {
                Holder<Biome> b = level.getBiome(pos);
                biomeName = b.unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("Unknown");
            }
        } catch (Throwable ignored) {}

        String time = LocalTime.now().format(TIME_FMT);

        return String.format(
                "▫️ <b>Координаты:</b> <code>X: %d, Y: %d, Z: %d</code>\n" +
                "▫️ <b>Измерение:</b> <code>%s</code>\n" +
                "▫️ <b>Биом:</b> %s\n" +
                "▫️ <b>Время:</b> <code>%s</code>\n" +
                "▫️ <b>Телепорт:</b> <code>/tp %d %d %d</code>",
                x, y, z,
                escapeHtml(dim),
                escapeHtml(biomeName),
                time,
                x, y, z
        );
    }

    public static class TelegramApiException extends Exception {
        public TelegramApiException(String message) {
            super(message);
        }
    }
}

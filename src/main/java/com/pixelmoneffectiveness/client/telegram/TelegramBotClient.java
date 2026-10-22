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
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final BlockingQueue<String> OUTBOX = new LinkedBlockingQueue<>(1000);
    private static final Map<UUID, Long> NOTIFIED_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> NOTIFIED_POSITIONS = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRE_MS = 20L * 60L * 1000L; // 20 minutes

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Thread WORKER_THREAD = new Thread(TelegramBotClient::runWorkerLoop, "Pixelmon-Telegram-Sender");

    static {
        WORKER_THREAD.setDaemon(true);
        WORKER_THREAD.start();
    }

    public static boolean isConfigured() {
        if (EffectivenessConfig.CONFIG == null) return false;
        try {
            if (!EffectivenessConfig.CONFIG.telegramEnabled.get()) return false;
            String token = EffectivenessConfig.CONFIG.telegramBotToken.get().trim();
            String chatId = EffectivenessConfig.CONFIG.telegramChatId.get().trim();
            return !token.isEmpty() && !chatId.isEmpty();
        } catch (Throwable t) {
            return false;
        }
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
        try {
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
        } catch (Throwable ignored) {}
        return false;
    }

    public static Set<String> getTrackedPokemonSet() {
        Set<String> set = new LinkedHashSet<>();
        if (EffectivenessConfig.CONFIG != null) {
            try {
                String filter = EffectivenessConfig.CONFIG.telegramPokemonFilter.get().trim();
                if (!filter.isEmpty()) {
                    for (String s : filter.split("[,;]+")) {
                        String t = s.trim();
                        if (!t.isEmpty()) {
                            set.add(t);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return set;
    }

    public static void setTrackedPokemonSet(Set<String> names) {
        if (EffectivenessConfig.CONFIG != null) {
            try {
                String joined = String.join(", ", names);
                EffectivenessConfig.CONFIG.telegramPokemonFilter.set(joined);
                EffectivenessConfig.SPEC.save();
            } catch (Throwable ignored) {}
        }
    }

    public static boolean toggleEnabled() {
        if (EffectivenessConfig.CONFIG == null) return false;
        try {
            boolean now = !EffectivenessConfig.CONFIG.telegramEnabled.get();
            EffectivenessConfig.CONFIG.telegramEnabled.set(now);
            EffectivenessConfig.SPEC.save();
            return now;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getQueueSize() {
        return OUTBOX.size();
    }

    /**
     * Enqueues a notification to be sent to Telegram.
     * Guaranteed delivery: messages are retained in the queue until successfully delivered (HTTP 200).
     *
     * @param htmlMessage HTML-formatted text
     * @return true if enqueued or already in queue; false if not configured/disabled
     */
    public static boolean sendNotification(String htmlMessage) {
        if (htmlMessage == null || htmlMessage.trim().isEmpty()) return false;
        if (!isConfigured()) return false;
        if (!OUTBOX.contains(htmlMessage)) {
            boolean added = OUTBOX.offer(htmlMessage);
            if (added) {
                LOGGER.debug("Enqueued Telegram notification (Queue size: {})", OUTBOX.size());
            }
            return added;
        }
        return true; // Already waiting in queue
    }

    /**
     * Dedicated background worker loop that processes the queue.
     * Guarantees 100% delivery:
     * - Only removes the message (OUTBOX.poll()) upon successful HTTP 200.
     * - Retries with backoff on network errors or timeouts.
     * - Handles HTTP 429 Too Many Requests by waiting the required retry_after seconds.
     * - Maintains at least 1.1s delay between messages to respect Telegram rate limits.
     */
    private static void runWorkerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Peek at the head of the queue WITHOUT removing it
                String msg = OUTBOX.peek();
                if (msg == null) {
                    // Queue empty: block until a new notification is added
                    msg = OUTBOX.take();
                }

                if (!isConfigured()) {
                    Thread.sleep(2000L);
                    continue;
                }

                String token = EffectivenessConfig.CONFIG.telegramBotToken.get().trim();
                String chatId = EffectivenessConfig.CONFIG.telegramChatId.get().trim();
                if (token.isEmpty() || chatId.isEmpty()) {
                    Thread.sleep(2000L);
                    continue;
                }

                boolean sentSuccessfully = false;
                long backoffDelayMs = 2000L;

                try {
                    sendHttpRequest(token, chatId, msg);
                    sentSuccessfully = true;
                } catch (TelegramRateLimitException rle) {
                    int retrySec = rle.getRetryAfterSeconds();
                    backoffDelayMs = Math.max(2, retrySec + 1) * 1000L;
                    LOGGER.warn("Telegram rate limit hit (429), backing off for {}s before retrying...", backoffDelayMs / 1000L);
                } catch (TelegramApiException apiEx) {
                    LOGGER.error("Telegram API error: {}", apiEx.getMessage());
                    if (apiEx.getStatusCode() >= 400 && apiEx.getStatusCode() < 500 && apiEx.getStatusCode() != 429) {
                        // Unrecoverable client error (e.g. 400 Bad Request, 401 Unauthorized, 404 Not Found)
                        // Discard this single invalid message to prevent deadlocking the queue
                        LOGGER.error("Dropping unsendable message due to HTTP client error {}: {}", apiEx.getStatusCode(), apiEx.getMessage());
                        sentSuccessfully = true;
                    } else {
                        // Server-side 5xx error: retry after 5s
                        backoffDelayMs = 5000L;
                    }
                } catch (Throwable netErr) {
                    LOGGER.warn("Network error sending to Telegram: {} (will retry in 3s, queue size: {})", netErr.getMessage(), OUTBOX.size());
                    backoffDelayMs = 3000L;
                }

                if (sentSuccessfully) {
                    // Remove confirmed message from queue
                    OUTBOX.poll();
                    // Respect Telegram rate limit: ~1 msg/sec per chat
                    Thread.sleep(1100L);
                } else {
                    // Do NOT poll! Keep at head of queue and wait backoff delay before retrying
                    Thread.sleep(backoffDelayMs);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                LOGGER.error("Unexpected error in Telegram sender loop: {}", t.getMessage());
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
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
                sendHttpRequest(token.trim(), chatId.trim(), testHtml);
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
                .timeout(Duration.ofSeconds(15))
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

            if (response.statusCode() == 429) {
                int retryAfter = 5;
                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("parameters")) {
                        JsonObject params = json.getAsJsonObject("parameters");
                        if (params.has("retry_after")) {
                            retryAfter = params.get("retry_after").getAsInt();
                        }
                    }
                } catch (Throwable ignored) {}
                throw new TelegramRateLimitException(retryAfter);
            }

            throw new TelegramApiException(response.statusCode(), errorMsg);
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
        private final int statusCode;
        public TelegramApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
        public int getStatusCode() {
            return statusCode;
        }
    }

    public static class TelegramRateLimitException extends Exception {
        private final int retryAfterSeconds;
        public TelegramRateLimitException(int retryAfterSeconds) {
            super("Too Many Requests: retry after " + retryAfterSeconds + "s");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}

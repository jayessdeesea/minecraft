package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.ChatReply;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderChat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The operator's conversation with the platoon leader, over HTTP on the loopback address only
 * ({@code minecraft.operator-chat.port}): a chat page at {@code /}, the overworld clock as JSON at {@code GET /clock},
 * and {@code POST /chat}, which takes the operator's message with the conversation's earlier turns and answers with
 * the platoon leader's reply ({@link PlatoonLeaderChat}). The page holds the conversation; nothing here does.
 *
 * <p>Each turn is logged and keyed out on the radio net ({@code [Operator -> PL]}, {@code [PL -> Operator]}), its model
 * call timed through the chain of command's queue ({@link ModelCalls}) on this server's own threads, never a
 * soldier's. The reply's action is one the operator's chat orders already have, given through the same path
 * ({@link ChainOfCommand#onOperatorMission} and the rest); a farm answers what it would be and that it can't be placed
 * yet. A request that fails is answered 500 with why, and logged; the next is served.
 *
 * <p>Only a request addressed to this machine by name or loopback address is served, and {@code POST /chat} only as
 * JSON: a page from elsewhere in the operator's browser can neither read the answers nor give orders.
 */
@Component
public class OperatorChat implements DisposableBean {

    private static final Logger LOG = LogManager.getLogger(OperatorChat.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Who the chat's turns and orders are from, on the net and in the log. */
    static final String OPERATOR = "Operator";
    /** Requests served at once: a clock fetch isn't held behind a model call. */
    static final int THREADS = 4;

    /** {@code POST /chat}'s body: the conversation so far, and the operator's new message. */
    record ChatRequest(List<PlatoonLeaderChat.Turn> turns, String message) {
    }

    /** {@code POST /chat}'s answer: the platoon leader's reply as said on the net, and the action it took. */
    record ChatResponse(String reply, ChatReply.Action action, String argument) {
    }

    /**
     * {@code GET /clock}'s answer: the overworld clock as last sent, for the page to tick forward between fetches -
     * {@code known} false, and the rest zero, until the server has sent it.
     */
    record Clock(boolean known, boolean running, boolean dark, long ticks, String clock, long ticksToDawn, long ticksToDusk) {

        static Clock of(Optional<TimeOfDay> time) {
            return time.map(t -> new Clock(true, t.running(), t.dark(), t.ticks(), t.clock().toString(), t.ticksToDawn(), t.ticksToDusk()))
                    .orElse(new Clock(false, false, false, 0, "", 0, 0));
        }
    }

    private final PlatoonLeaderChat chat;
    private final ChainOfCommand chain;
    private final HttpServer server;
    private final ExecutorService threads;
    private final byte[] page;

    public OperatorChat(PlatoonLeaderChat chat, ChainOfCommand chain, @Value("${minecraft.operator-chat.port}") int port) {
        this.chat = chat;
        this.chain = chain;
        this.page = page();
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("couldn't open the operator's chat on 127.0.0.1:" + port, e);
        }
        AtomicInteger n = new AtomicInteger();
        threads = Executors.newFixedThreadPool(THREADS, r -> new Thread(r, "operator-chat-" + n.incrementAndGet()));
        server.setExecutor(threads);
        server.createContext("/", this::serve);
        server.start();
        LOG.info("[Chat] the operator's chat with the platoon leader is at http://127.0.0.1:{}/", port());
    }

    /** The port it's served on: the configured one, or the one the system gave for 0. */
    int port() {
        return server.getAddress().getPort();
    }

    private static byte[] page() {
        try (InputStream in = OperatorChat.class.getResourceAsStream("/operator-chat.html")) {
            if (in == null) {
                throw new IllegalStateException("operator-chat.html isn't in the app's resources");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("couldn't read operator-chat.html", e);
        }
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (!local(exchange.getRequestHeaders().getFirst("Host"))) {
                send(exchange, 403, "text/plain", "only this machine's own address is served".getBytes(StandardCharsets.UTF_8));
            } else if (method.equals("GET") && path.equals("/")) {
                send(exchange, 200, "text/html; charset=utf-8", page);
            } else if (method.equals("GET") && path.equals("/clock")) {
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(Clock.of(chain.time())));
            } else if (method.equals("POST") && path.equals("/chat")) {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (type == null || !type.startsWith("application/json")) {
                    send(exchange, 415, "text/plain", "POST /chat takes application/json".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                ChatRequest request = JSON.readValue(exchange.getRequestBody(), ChatRequest.class);
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(turn(request)));
            } else {
                send(exchange, 404, "text/plain", ("no " + method + " " + path).getBytes(StandardCharsets.UTF_8));
            }
        } catch (RuntimeException | IOException e) {
            LOG.error("[Chat] {} {} failed: {}", exchange.getRequestMethod(), exchange.getRequestURI(), e.getMessage(), e);
            send(exchange, 500, "application/json", JSON.writeValueAsBytes(Map.of("error", String.valueOf(e.getMessage()))));
        } finally {
            exchange.close();
        }
    }

    /** Whether {@code host} - the request's Host header - names this machine: its loopback address or localhost. */
    static boolean local(String host) {
        if (host == null) {
            return false;
        }
        String name = host.startsWith("[") ? host.substring(0, host.indexOf(']') + 1) : host.replaceFirst(":\\d+$", "");
        return name.equals("127.0.0.1") || name.equals("localhost") || name.equals("[::1]");
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    /** One of the operator's turns: said on the net, answered through the model queue, its action given. */
    ChatResponse turn(ChatRequest request) {
        List<PlatoonLeaderChat.Turn> earlier = request.turns() == null ? List.of() : request.turns();
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("an empty message");
        }
        LOG.info("[{} -> PL] {}", OPERATOR, message);
        chain.radio("[" + OPERATOR + " -> PL] " + message);
        ChatReply reply;
        ModelCalls.Ticket ticket = chain.modelCalls().queued("Operator chat");
        ticket.started();
        try {
            reply = chat.answer(earlier, message, chain.time(), chain.flatten(), chain.objectiveBook.map(b -> b.describe(chain.clockwork.millis())));
        } finally {
            ticket.finished();
        }
        String argument = reply.argument() == null ? "" : reply.argument().strip();
        Runnable order = order(reply.action(), argument);
        String said = reply.reply().strip() + (reply.action() == ChatReply.Action.FARM ? " " + farm(argument) : "");
        LOG.info("[PL -> {}] {}{}", OPERATOR, said, reply.action() == ChatReply.Action.NONE ? "" : " (" + reply.action() + " " + argument + ")");
        chain.radio("[PL -> " + OPERATOR + "] " + said);
        order.run();
        return new ChatResponse(said, reply.action(), argument);
    }

    /**
     * The order {@code action} gives, through the same path as the operator's chat orders - read before anything is said,
     * so an argument it can't read fails the turn, loud, with nothing given.
     */
    private Runnable order(ChatReply.Action action, String argument) {
        if (action != ChatReply.Action.NONE && action != ChatReply.Action.FARM && argument.isEmpty()) {
            throw new IllegalStateException("the platoon leader's " + action + " has no argument");
        }
        return switch (action) {
            case NONE, FARM -> () -> { };
            case MISSION -> () -> chain.onOperatorMission(OPERATOR, argument);
            case ESCORT -> () -> chain.onOperatorEscort(OPERATOR, argument);
            case FLATTEN -> {
                Flatten site = Flatten.parse(argument).orElseThrow(() -> new IllegalStateException(
                        "the platoon leader's FLATTEN isn't \"x1 z1 x2 z2 y\": " + argument));
                yield () -> chain.onOperatorFlatten(OPERATOR, site);
            }
        };
    }

    /**
     * What a farm at {@code argument} ("x z", or "x z y") would be, and that it can't be placed yet: a chunk farm laid
     * out on the chunk the point is in, at sea level unless a level is given - computed, not left to the model.
     */
    static String farm(String argument) {
        String[] words = argument.split("[\\s,]+");
        if (words.length != 2 && words.length != 3) {
            throw new IllegalStateException("the platoon leader's FARM isn't \"x z\" or \"x z y\": " + argument);
        }
        int x;
        int z;
        int level;
        try {
            x = Integer.parseInt(words[0]);
            z = Integer.parseInt(words[1]);
            level = words.length == 3 ? Integer.parseInt(words[2]) : ChunkFarm.SEA_LEVEL;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("the platoon leader's FARM isn't \"x z\" or \"x z y\": " + argument, e);
        }
        ChunkFarm farm = new ChunkFarm(Math.floorDiv(x, ChunkFarm.SIDE), Math.floorDiv(z, ChunkFarm.SIDE), level);
        int x0 = farm.chunkX() * ChunkFarm.SIDE;
        int z0 = farm.chunkZ() * ChunkFarm.SIDE;
        return String.format(Locale.ROOT, "It would be a chunk farm on chunk (%d, %d) - x %d to %d, z %d to %d, fenced on the"
                        + " chunk's lines - at y=%d%s, put on the platoon leader's sites through its bids and build order. CANTCO:"
                        + " the bid cycle isn't built yet.", farm.chunkX(), farm.chunkZ(), x0, x0 + ChunkFarm.SIDE, z0, z0 + ChunkFarm.SIDE,
                farm.level(), words.length == 3 ? "" : " (sea level)");
    }

    @Override
    public void destroy() {
        server.stop(0);
        threads.shutdownNow();
    }
}

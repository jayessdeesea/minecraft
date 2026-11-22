package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
 * ({@code minecraft.operator-chat.port}): a chat page at {@code /}, the overworld clock as JSON at {@code GET /clock}, the garrison's status - each squad's
 * objective and what each soldier is doing - at {@code GET /status} ({@link StatusBoard}),
 * and {@code POST /chat}, which takes the operator's message with the conversation's earlier turns and answers with
 * the platoon leader's reply ({@link PlatoonLeaderChat}). The page holds the conversation; nothing here does.
 *
 * <p>Each turn is logged and keyed out on the radio net ({@code [Operator -> PL]}, {@code [PL -> Operator]}), its model
 * call timed through the chain of command's queue ({@link ModelCalls}) on this server's own threads, never a
 * soldier's. The platoon leader's commands are its tools' ({@link ChatCommands}), each one of the operator's own orders
 * through the same path ({@link ChainOfCommand#onOperatorMission} and the rest); what each did is answered with the reply,
 * as computed. A request that fails is answered 500 with why, and logged; the next is served.
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

    /** {@code POST /chat}'s answer: the platoon leader's reply as said on the net, and what each of its commands did. */
    record ChatResponse(String reply, List<String> done) {
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
    private final StatusBoard board;
    private final HttpServer server;
    private final ExecutorService threads;
    private final byte[] page;

    public OperatorChat(PlatoonLeaderChat chat, ChainOfCommand chain, StatusBoard board, @Value("${minecraft.operator-chat.port}") int port) {
        this.chat = chat;
        this.chain = chain;
        this.board = board;
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
            } else if (method.equals("GET") && path.equals("/status")) {
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(board.now()));
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

    /** One of the operator's turns: said on the net, answered through the model queue, its commands given as it calls them. */
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
            reply = chat.answer(earlier, message, chain.time(), chain.flatten());
        } finally {
            ticket.finished();
        }
        LOG.info("[PL -> {}] {}{}", OPERATOR, reply.reply(), reply.done().isEmpty() ? "" : " (did: " + String.join(" | ", reply.done()) + ")");
        chain.radio("[PL -> " + OPERATOR + "] " + reply.reply());
        return new ChatResponse(reply.reply(), reply.done());
    }

    @Override
    public void destroy() {
        server.stop(0);
        threads.shutdownNow();
    }
}

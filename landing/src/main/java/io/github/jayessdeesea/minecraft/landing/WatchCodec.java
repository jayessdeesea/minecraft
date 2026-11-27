package io.github.jayessdeesea.minecraft.landing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The watchdog's two files as JSON and back: the verify history and the progress of the verify running. Times are
 * {@link Instant#toString}'s, spans whole milliseconds. Text that is not one is refused, named; the files are the
 * shell's, each a witness - one unreadable is said and the queue goes on without it.
 */
public final class WatchCodec {

    private final JsonMapper json = JsonMapper.builder().build();

    public String write(VerifyHistory history) {
        ObjectNode root = json.createObjectNode();
        ArrayNode passes = root.putArray("passes");
        for (VerifyPass p : history.passes()) {
            ObjectNode pass = passes.addObject();
            pass.put("at", p.at().toString());
            pass.put("candidate", p.candidate().value());
            pass.put("tookMillis", p.took().toMillis());
            ArrayNode modules = pass.putArray("modules");
            for (VerifyPass.Module m : p.modules()) {
                ObjectNode module = modules.addObject();
                module.put("name", m.name());
                module.put("wallMillis", m.wall().toMillis());
                ObjectNode classes = module.putObject("classMillis");
                m.classes().forEach((c, t) -> classes.put(c, t.toMillis()));
            }
        }
        return text(root);
    }

    public VerifyHistory history(String text) {
        try {
            JsonNode root = tree(text);
            List<VerifyPass> passes = new ArrayList<>();
            for (JsonNode pass : need(root, "passes")) {
                List<VerifyPass.Module> modules = new ArrayList<>();
                for (JsonNode module : need(pass, "modules")) {
                    Map<String, Duration> classes = new LinkedHashMap<>();
                    need(module, "classMillis").fields().forEachRemaining(e -> classes.put(e.getKey(), millis(e.getValue())));
                    modules.add(new VerifyPass.Module(need(module, "name").asText(), millis(need(module, "wallMillis")), classes));
                }
                passes.add(new VerifyPass(Instant.parse(need(pass, "at").asText()), Sha.parse(need(pass, "candidate").asText()),
                        millis(need(pass, "tookMillis")), modules));
            }
            return new VerifyHistory(passes);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a verify history: " + e.getMessage(), e);
        }
    }

    public String write(VerifyProgress p) {
        ObjectNode root = json.createObjectNode();
        root.put("id", p.id().value());
        root.put("candidate", p.candidate().value());
        root.put("started", p.started().toString());
        root.put("at", p.at().toString());
        root.put("lastGrew", p.lastGrew().toString());
        root.put("logBytes", p.logBytes());
        root.put("stallMillis", p.stall().toMillis());
        root.put("capMillis", p.cap().toMillis());
        ObjectNode maven = root.putObject("maven");
        p.maven().module().ifPresent(m -> maven.put("module", m));
        maven.put("index", p.maven().index());
        maven.put("of", p.maven().of());
        maven.put("classesDone", p.maven().classesDone());
        maven.put("classesFailing", p.maven().classesFailing());
        p.estimate().ifPresent(e -> {
            ObjectNode estimate = root.putObject("estimate");
            estimate.put("percent", e.percent());
            estimate.put("leftMillis", e.left().toMillis());
            e.over().ifPresent(o -> estimate.put("overMillis", o.toMillis()));
            estimate.put("expectedMillis", e.expected().toMillis());
            estimate.put("passes", e.passes());
            estimate.put("classes", e.classes());
        });
        return text(root);
    }

    public VerifyProgress progress(String text) {
        try {
            JsonNode root = tree(text);
            JsonNode maven = need(root, "maven");
            Optional<VerifyEstimate> estimate = Optional.ofNullable(root.get("estimate")).map(e -> new VerifyEstimate(
                    need(e, "percent").asInt(), millis(need(e, "leftMillis")),
                    Optional.ofNullable(e.get("overMillis")).map(WatchCodec::millis), millis(need(e, "expectedMillis")),
                    need(e, "passes").asInt(), need(e, "classes").asInt()));
            return new VerifyProgress(EntryId.parse(need(root, "id").asText()), Sha.parse(need(root, "candidate").asText()),
                    Instant.parse(need(root, "started").asText()), Instant.parse(need(root, "at").asText()),
                    Instant.parse(need(root, "lastGrew").asText()), need(root, "logBytes").asLong(),
                    millis(need(root, "stallMillis")), millis(need(root, "capMillis")),
                    new MavenProgress(Optional.ofNullable(maven.get("module")).map(JsonNode::asText), need(maven, "index").asInt(),
                            need(maven, "of").asInt(), need(maven, "classesDone").asInt(), need(maven, "classesFailing").asInt()),
                    estimate);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a verify's progress: " + e.getMessage(), e);
        }
    }

    private String text(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("not written as JSON", e);
        }
    }

    private JsonNode tree(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getOriginalMessage(), e);
        }
    }

    private static JsonNode need(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("no " + field);
        }
        return value;
    }

    private static Duration millis(JsonNode node) {
        if (!node.canConvertToLong()) {
            throw new IllegalArgumentException("not a number of milliseconds: " + node);
        }
        return Duration.ofMillis(node.asLong());
    }
}

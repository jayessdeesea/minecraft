package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.OperatorCommands;
import io.github.jayessdeesea.minecraft.command.RegistersSaid;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The operator's chat's commands, each given as the operator's own order through the chain of command
 * ({@link ChainOfCommand#onOperatorFarm} and the rest): the same WILCO on the net and order trace as from game chat, and
 * the same checks - a farm's level within the world, a flatten's corners. What each says it did is computed here.
 */
@Component("operatorCommands")
public class ChatCommands implements OperatorCommands {

    private final ChainOfCommand chain;

    public ChatCommands(ChainOfCommand chain) {
        this.chain = chain;
    }

    @Override
    public String giveFarmMission(int x, int z, OptionalInt level) {
        return give(x, z, level, FarmMission.Crop.WHEAT);
    }

    @Override
    public String giveTreeFarmMission(int x, int z, OptionalInt level) {
        return give(x, z, level, FarmMission.Crop.TREES);
    }

    @Override
    public String giveSupplyPointMission(int x, int z, OptionalInt level) {
        return give(x, z, level, FarmMission.Crop.STORES);
    }

    private String give(int x, int z, OptionalInt level, FarmMission.Crop crop) {
        String order = x + " " + z + (level.isPresent() ? " " + level.getAsInt() : "");
        FarmMission farm = FarmMission.parse(order, crop).orElseThrow(() -> new IllegalArgumentException(
                "no farm mission at x=" + x + " z=" + z + (level.isPresent() ? " y=" + level.getAsInt() : "") + ": its level must be inside the world"));
        chain.onOperatorFarm(OperatorChat.OPERATOR, farm);
        return "WILCO: " + farm.name() + ", in " + farm.steps().size() + " phases, each started when the one before is measured done: "
                + String.join("; ", farm.steps().stream().map(s -> "phase " + (s.index() + 1) + ", " + s.task()).toList()) + ".";
    }

    @Override
    public String cancelMission(int chunkX, int chunkZ, String reason) {
        String why = reason == null || reason.isBlank() ? "no reason given" : reason.strip();
        Optional<FarmMission> cancelled = chain.onOperatorCancel(OperatorChat.OPERATOR, chunkX, chunkZ, why);
        return cancelled.map(m -> "WILCO: " + m.name() + " cancelled - its phases not done closed SUPERSEDED, \"" + why + "\" kept as why.")
                .orElse("CANTCO: no farm mission is open on " + RegistersSaid.chunkSaid(chunkX, chunkZ) + " - nothing cancelled.");
    }

    @Override
    public String replacePlatoonMission(String mission) {
        if (mission == null || mission.isBlank()) {
            throw new IllegalArgumentException("an empty mission");
        }
        chain.onOperatorMission(OperatorChat.OPERATOR, mission.strip());
        return "WILCO: the platoon's mission is now \"" + mission.strip() + "\".";
    }

    @Override
    public String escort(String player) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException("no player named to escort");
        }
        chain.onOperatorEscort(OperatorChat.OPERATOR, player.strip());
        return "WILCO: the first squad escorts " + player.strip() + " until the platoon's mission is replaced.";
    }

    @Override
    public String setFlattenGround(Flatten ground) {
        chain.onOperatorFlatten(OperatorChat.OPERATOR, ground);
        Flatten worked = chain.flatten();
        return "WILCO: the ground to flatten is " + ground.describe() + (worked.equals(ground) ? "." : "; the farm mission's flatten, "
                + worked.describe() + ", is worked first while it's under way.");
    }
}

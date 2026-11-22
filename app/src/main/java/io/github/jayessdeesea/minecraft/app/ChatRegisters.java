package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.PlatoonRegisters;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The platoon leader's registers as the operator's chat reads them: the garrison's {@link Registers}, through the chain of
 * command's {@link ObjectiveBook}, on its clock. Read only.
 */
@Component("platoonRegisters")
public class ChatRegisters implements PlatoonRegisters {

    private final ChainOfCommand chain;

    public ChatRegisters(ChainOfCommand chain) {
        this.chain = chain;
    }

    @Override
    public List<ObjectiveRecord> tasks() {
        return registers().objectives();
    }

    @Override
    public List<ChunkRecord> chunks() {
        return registers().chunks();
    }

    @Override
    public long now() {
        return chain.clockwork.millis();
    }

    private Registers registers() {
        return chain.objectiveBook.orElseThrow(() -> new IllegalStateException("no registers are kept here: the chain of command keeps no objective book"))
                .registers();
    }
}

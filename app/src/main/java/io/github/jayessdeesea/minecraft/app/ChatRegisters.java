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
    /** The status board, asked for when first read: it's built on the runner, which is built after this. */
    private final org.springframework.beans.factory.ObjectProvider<StatusBoard> board;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatRegisters(ChainOfCommand chain, org.springframework.beans.factory.ObjectProvider<StatusBoard> board) {
        this.chain = chain;
        this.board = board;
    }

    /** With no status board: the registers alone, nobody's pack kept. */
    public ChatRegisters(ChainOfCommand chain) {
        this(chain, null);
    }

    @Override
    public java.util.Map<String, java.util.Map<String, Integer>> carried() {
        if (board == null) {
            throw new IllegalStateException("no soldier's pack is kept here: no status board");
        }
        return board.getObject().carried();
    }

    @Override
    public java.util.Map<String, Integer> stores() {
        return GarrisonStatus.stores(chain.stores());
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

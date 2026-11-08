package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.core.io.ClassPathResource;

/** The echelons' real prompt files, for tests: the ones {@code app}'s application.yml names. */
final class Prompts {

    static final GeneralPrompts GENERAL = GeneralPrompts.read(new ClassPathResource("prompts/general-system-prompt.md"),
            new ClassPathResource("prompts/general-user-prompt.md"), new ClassPathResource("prompts/general-squad-prompt.md"));
    static final SquadLeaderPrompts SQUAD_LEADER = SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
            new ClassPathResource("prompts/squad-leader-user-prompt.md"));

    private Prompts() {
    }
}

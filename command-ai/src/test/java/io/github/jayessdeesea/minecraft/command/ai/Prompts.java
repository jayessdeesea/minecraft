package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.core.io.ClassPathResource;

/** The echelons' real prompt files, for tests: the ones {@code app}'s application.yml names. */
final class Prompts {

    static final PlatoonLeaderPrompts PLATOON_LEADER = PlatoonLeaderPrompts.read(new ClassPathResource("prompts/platoon-leader-system-prompt.md"),
            new ClassPathResource("prompts/platoon-leader-user-prompt.md"), new ClassPathResource("prompts/platoon-leader-squad-prompt.md"));
    static final SquadLeaderPrompts SQUAD_LEADER = SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
            new ClassPathResource("prompts/squad-leader-user-prompt.md"));

    private Prompts() {
    }
}

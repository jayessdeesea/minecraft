package io.github.jayessdeesea.minecraft.command.ai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What the prompt files rely on in {@link PromptTemplate}'s rendering. */
class PromptTemplateTest {

    @Test
    void parametersAreMarkedWithAngleQuotesAndBracesAndTagsAreText() {
        assertEquals("<mission>\nHold {\"keep\": false}\n</mission>",
                PromptTemplate.render("<mission>\n«mission» {\"keep\": false}\n</mission>", Map.of("mission", "Hold")));
    }

    @Test
    void aValueIsPutInAsItIsNeverReadAsATemplate() {
        assertEquals("x «y» {z} <a> & \\n", PromptTemplate.render("«v»", Map.of("v", "x «y» {z} <a> & \\n")));
    }

    @Test
    void aSectionShownOnlySometimesLeavesNoBlankLineWhenItIsnt() {
        String template = "a\n«if(time)»\n<time>\n«time»\n</time>\n\n«endif»\nb";
        Map<String, Object> none = new HashMap<>();
        none.put("time", null);

        assertEquals("a\n<time>\nDay.\n</time>\n\nb", PromptTemplate.render(template, Map.of("time", "Day.")));
        assertEquals("a\nb", PromptTemplate.render(template, none));
        assertEquals("a\nb", PromptTemplate.render(template, Map.of("time", false)));
        assertEquals("a\nb", PromptTemplate.render(template, Map.of("time", List.of())));
    }

    @Test
    void aParameterTheCallDoesntGiveFailsTheRender() {
        assertThrows(IllegalStateException.class, () -> PromptTemplate.render("«mission» «time»", Map.of("mission", "Hold")));
    }
}

package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * How a prompt file is read and filled in. Parameters are marked {@code «name»} - angle quotation
 * marks, not StringTemplate's default braces, so the JSON and XML-tagged text in a prompt is
 * never taken for one; a section shown only sometimes is {@code «if(name)»...«endif»}. A
 * parameter the template names and the call doesn't give fails the render
 * ({@link ValidationMode#THROW}): AGENTS.md "No fallbacks".
 */
public final class PromptTemplate {

    private static final TemplateRenderer ST = StTemplateRenderer.builder()
            .startDelimiterToken('«')
            .endDelimiterToken('»')
            .validationMode(ValidationMode.THROW)
            .build();

    /**
     * The one renderer: the model call and every reading of a prompt (logs, evals) fill a template
     * the same way. StringTemplate ends its lines with the platform's separator; a prompt's are
     * {@code \n} on every platform.
     */
    static final TemplateRenderer RENDERER = (template, params) -> ST.apply(template, params).replace("\r\n", "\n");

    private PromptTemplate() {
    }

    /** {@code template} filled in with {@code params}. */
    static String render(String template, Map<String, Object> params) {
        return RENDERER.apply(template, params);
    }

    /** A prompt file's text, its line breaks {@code \n} whatever the checkout wrote. */
    public static String read(Resource prompt) {
        try {
            return prompt.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("couldn't read the prompt " + prompt, e);
        }
    }
}

/**
 * Spring AI-backed {@code General} and {@code SquadLeader} implementations. The model provider
 * (local Ollama by default, Anthropic as the alternative) is a Spring config concern - keep
 * provider-specific types out of this package. System prompts live in {@code prompts/*.md}
 * resources; replies come back as structured output ({@code ChatClient...entity(...)}).
 */
package io.github.jayessdeesea.minecraft.command.ai;

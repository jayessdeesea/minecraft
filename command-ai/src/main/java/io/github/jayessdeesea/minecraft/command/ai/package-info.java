/**
 * Spring AI-backed {@code General} and {@code SquadLeader} implementations. The model provider
 * (local Ollama by default, Anthropic as the alternative) is a Spring config concern - keep
 * provider-specific types out of this package. Each echelon's system prompt and user prompt template
 * are {@code prompts/*.md} resources that configuration names ({@link CommandAiConfiguration});
 * replies come back as structured output ({@code ChatClient...entity(...)}).
 */
package io.github.jayessdeesea.minecraft.command.ai;

package org.gms.extension.event;

import org.gms.client.Character;
import org.gms.extension.api.HostEvent;

/**
 * A player sent a whisper, buddy, party, guild or alliance chat line <em>to</em> {@code recipient}.
 *
 * <p>Published once per artificial recipient (a character owned by a plugin that has no real client
 * to read the chat packet), the same way {@link CharacterChatEvent} is published only for real
 * senders. The sender in a bot-to-bot or bot-to-player line does not produce an event - only an
 * artificial recipient does, since that is the case the engine's own {@code sendPacket} cannot serve.
 */
public record CharacterDirectChatEvent(
        Character sender, Character recipient, String message, ChatType type) implements HostEvent {
}

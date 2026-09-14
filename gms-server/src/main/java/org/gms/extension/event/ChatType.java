package org.gms.extension.event;

/**
 * The player-to-player channel a {@link CharacterDirectChatEvent} travelled on. Mirrors the client's
 * {@code MULTI_CHAT} {@code type} (buddy / party / guild / alliance) plus whisper, which has its own
 * handler. Map (general) chat is {@link CharacterChatEvent}, not one of these.
 */
public enum ChatType {
    WHISPER,
    BUDDY,
    PARTY,
    GUILD,
    ALLIANCE
}

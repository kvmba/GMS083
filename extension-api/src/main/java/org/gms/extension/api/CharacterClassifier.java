package org.gms.extension.api;

/**
 * Decides whether a character id is owned by a plugin (bots, NPCs-as-characters, etc.).
 * Registered by plugins via {@link ArtificialCharacters#register(CharacterClassifier)}.
 *
 * <p><b>Performance contract.</b> Implementations must be cheap, non-blocking and
 * side-effect free. The host calls {@link ArtificialCharacters#isArtificial(int)} from
 * hot engine paths — notably {@code MapleMap.broadcastMessage} and
 * {@code broadcastBossHpMessage}, which invoke it once per character on every single
 * broadcast, plus controller election, timeout sweeps and trade/visit checks. An
 * implementation that performs I/O, takes a lock, or scans a collection will degrade
 * or stall the whole world.
 *
 * <p>Prefer an O(1) in-memory set lookup. Do not query the database here.
 */
@FunctionalInterface
public interface CharacterClassifier {

    boolean isArtificial(int characterId);
}

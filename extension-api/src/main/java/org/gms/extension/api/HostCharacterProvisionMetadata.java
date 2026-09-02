package org.gms.extension.api;

import java.util.Objects;

/**
 * Native identity handed to an extension inside the host's provisioning
 * transaction, so extension rows can reference the new character without a
 * second lookup.
 *
 * @param characterId    the newly inserted native character id
 * @param accountId      the newly inserted native account id
 * @param accountName    the account name that was provisioned
 * @param characterName  the character name that was provisioned
 * @param worldId        the world the character was created in
 */
public record HostCharacterProvisionMetadata(
        int characterId,
        int accountId,
        String accountName,
        String characterName,
        int worldId
) {
    public HostCharacterProvisionMetadata {
        if (characterId <= 0 || accountId <= 0) {
            throw new IllegalArgumentException("provisioned ids must be positive");
        }
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(characterName, "characterName");
    }
}

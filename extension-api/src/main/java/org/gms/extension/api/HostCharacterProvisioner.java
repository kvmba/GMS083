package org.gms.extension.api;

/**
 * Atomic host-owned creation of an account and a native character.
 *
 * <p>The two-argument form is the primitive one: extension work runs on the same
 * connection, inside the same transaction, so extension tables stay consistent
 * with the native character without a second round trip. The single-argument
 * form is shorthand for "provision with no extra extension work".
 */
public interface HostCharacterProvisioner {

    /**
     * Provisions a native account and character, then runs {@code callback} on the
     * same connection before commit. If the callback throws, the native rows are
     * rolled back as well.
     *
     * @param request  provisioning input
     * @param callback extension work to run in-transaction; {@code null} means no extra work
     * @return the committed native identity
     * @throws Exception if provisioning or the callback failed; nothing is committed
     */
    HostCharacterProvisionResult provision(
            HostCharacterProvisionRequest request,
            HostCharacterMetadataCallback callback
    ) throws Exception;

    /**
     * Provisions a native account and character in the host's own transaction,
     * with no extension work attached.
     */
    default HostCharacterProvisionResult provision(HostCharacterProvisionRequest request) throws Exception {
        return provision(request, null);
    }
}

package org.gms.extension.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Extension work executed inside the host's provisioning transaction, after the
 * native account and character rows have been written but before commit.
 *
 * <p>Implementations may write their own business tables on the supplied
 * connection. They must not commit, roll back, close, or change transaction
 * settings on it — the host owns the transaction boundary. Throwing any
 * exception rolls the whole provisioning back, including the native rows.
 */
@FunctionalInterface
public interface HostCharacterMetadataCallback {

    /**
     * @param connection the host's open provisioning connection (never null)
     * @param metadata   the native identity written so far
     * @throws SQLException to roll the entire provisioning transaction back
     */
    void persist(Connection connection, HostCharacterProvisionMetadata metadata) throws SQLException;
}

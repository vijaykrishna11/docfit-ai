package com.docfitai.backend.provider.nppes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL session-level advisory lock, used to guarantee only one provider refresh runs at a
 * time (CLAUDE.md "Overlap Protection"). Deliberately uses a single raw JDBC {@link Connection}
 * taken directly from the {@link DataSource} -- not {@code JdbcTemplate}, whose separate calls can
 * each borrow a different pooled connection -- because {@code pg_advisory_lock}/{@code
 * pg_advisory_unlock} are scoped to the *session* (physical connection) that acquired them; the
 * same connection must hold the lock for the whole task and release it itself.
 */
@Component
public class ProviderRefreshLock {

    // Arbitrary, stable 64-bit key identifying "provider refresh" -- any fixed value works, it
    // only needs to never collide with another advisory lock this codebase might add later.
    private static final long LOCK_KEY = 8_374_629_101_235L;

    private final DataSource dataSource;

    public ProviderRefreshLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs {@code task} while holding the lock, or returns {@code false} immediately (never
     * blocks) if another refresh already holds it.
     */
    public boolean runIfNotAlreadyRunning(Runnable task) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                unlock(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire the provider refresh advisory lock", e);
        }
    }

    private boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        }
    }
}

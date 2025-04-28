package com.goodmem.operations;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.ApiKey;
import com.goodmem.db.ApiKeys;
import com.goodmem.db.User;
import com.goodmem.db.Users;
import org.tinylog.Logger;

import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Operation class for initializing the system.
 * This creates a root user and API key when the system is first set up.
 */
public class SystemInitOperation {
    private final Connection dbConnection;

    /**
     * Creates a new SystemInitOperation with the required database connection.
     *
     * @param dbConnection the database connection to use
     */
    public SystemInitOperation(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Result of the system initialization operation.
     */
    public record InitResult(boolean alreadyInitialized, String apiKey, UUID userId, String errorMessage) {

        public static InitResult success(String apiKey, UUID userId) {
            return new InitResult(false, apiKey, userId, null);
        }

        public static InitResult alreadyInitialized() {
            return new InitResult(true, null, null, null);
        }

        public static InitResult error(String errorMessage) {
            return new InitResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return errorMessage == null;
        }
    }

    /**
     * Initialize the system by creating a root user and API key.
     * If the root user already exists, it will return a result indicating that.
     *
     * @return the initialization result
     */
    public InitResult execute() {
        Logger.info("Executing system initialization operation");

        try {
            // Check if 'root' user already exists
            StatusOr<Optional<User>> rootUserOr = Users.loadByUsername(dbConnection, "root");
            if (rootUserOr.isNotOk()) {
                Logger.error("Failed to check for root user: {}", rootUserOr.getStatus().getMessage());
                return InitResult.error("Root user lookup failed.");
            }

            // If root user exists, return "already initialized" result
            if (rootUserOr.getValue().isPresent()) {
                Logger.info("System is already initialized");
                return InitResult.alreadyInitialized();
            }

            // Root user doesn't exist, create it
            UUID rootUserId = UUID.randomUUID();
            Instant now = Instant.now();

            User rootUser = new User(
                    rootUserId,
                    "root",
                    "root@goodmem.ai",
                    "System Root User",
                    now,
                    now
            );

            StatusOr<Integer> saveUserOr = Users.save(dbConnection, rootUser);
            if (saveUserOr.isNotOk()) {
                Logger.error("Failed to create root user: {}", saveUserOr.getStatus().getMessage());
                return InitResult.error("Failed to create root user.");
            }

            // Create an API key for the root user
            UUID apiKeyId = UUID.randomUUID();
            String rawApiKey = "gm_" + UUID.randomUUID().toString().replace("-", "");
            String keyPrefix = rawApiKey.substring(0, Math.min(rawApiKey.length(), 10));
            String keyHash = rawApiKey; // In a real system, this would be a secure hash

            ApiKey apiKey = new ApiKey(
                    apiKeyId,
                    rootUserId,
                    keyPrefix,
                    keyHash,
                    "ACTIVE",
                    Map.of("purpose", "admin"),
                    null, // No expiration
                    now,  // Last used now
                    now,  // Created now
                    now,  // Updated now
                    rootUserId, // Created by self (root)
                    rootUserId  // Updated by self (root)
            );

            StatusOr<Integer> saveApiKeyOr = ApiKeys.save(dbConnection, apiKey);
            if (saveApiKeyOr.isNotOk()) {
                String error = "Failed to create API key: " + saveApiKeyOr.getStatus().getMessage();
                logger.severe(error);
                return InitResult.error(error);
            }

            logger.info("System initialized successfully");
            return InitResult.success(rawApiKey, rootUserId);

        } catch (Exception e) {
            String error = "Error during system initialization: " + e.getMessage();
            logger.severe(error);
            e.printStackTrace();
            return InitResult.error(error);
        }
    }
}
package com.goodmem.operations;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.ApiKey;
import com.goodmem.db.ApiKeys;
import com.goodmem.db.User;
import com.goodmem.db.Users;

import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Operation class for initializing the system.
 * This creates a root user and API key when the system is first set up.
 */
public class SystemInitOperation {
    private static final Logger logger = Logger.getLogger(SystemInitOperation.class.getName());
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
    public static class InitResult {
        private final boolean alreadyInitialized;
        private final String apiKey;
        private final UUID userId;
        private final String errorMessage;

        private InitResult(boolean alreadyInitialized, String apiKey, UUID userId, String errorMessage) {
            this.alreadyInitialized = alreadyInitialized;
            this.apiKey = apiKey;
            this.userId = userId;
            this.errorMessage = errorMessage;
        }

        public static InitResult alreadyInitialized() {
            return new InitResult(true, null, null, null);
        }

        public static InitResult success(String apiKey, UUID userId) {
            return new InitResult(false, apiKey, userId, null);
        }

        public static InitResult error(String errorMessage) {
            return new InitResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return errorMessage == null;
        }

        public boolean isAlreadyInitialized() {
            return alreadyInitialized;
        }

        public String getApiKey() {
            return apiKey;
        }

        public UUID getUserId() {
            return userId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Initialize the system by creating a root user and API key.
     * If the root user already exists, it will return a result indicating that.
     *
     * @return the initialization result
     */
    public InitResult execute() {
        logger.info("Executing system initialization operation");

        try {
            // Check if 'root' user already exists
            StatusOr<Optional<User>> rootUserOr = Users.loadByUsername(dbConnection, "root");
            if (rootUserOr.isNotOk()) {
                String error = "Failed to check for root user: " + rootUserOr.getStatus().getMessage();
                logger.severe(error);
                return InitResult.error(error);
            }

            // If root user exists, return "already initialized" result
            if (rootUserOr.getValue().isPresent()) {
                logger.info("System is already initialized");
                return InitResult.alreadyInitialized();
            }

            // Root user doesn't exist, create it
            UUID rootUserId = UUID.randomUUID();
            Instant now = Instant.now();

            User rootUser = new User(
                    rootUserId,
                    "root",
                    "root@example.com",
                    "System Root User",
                    now,
                    now
            );

            StatusOr<Integer> saveUserOr = Users.save(dbConnection, rootUser);
            if (saveUserOr.isNotOk()) {
                String error = "Failed to create root user: " + saveUserOr.getStatus().getMessage();
                logger.severe(error);
                return InitResult.error(error);
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
package com.goodmem.rest.dto;

/**
 * Represents sort order options used in API requests.
 */
@ProtobufEquivalent(goodmem.v1.Common.SortOrder.class)
public enum SortOrder {
    /**
     * Sort in ascending order (A-Z, 0-9, oldest to newest).
     */
    ASCENDING,
    
    /**
     * Sort in descending order (Z-A, 9-0, newest to oldest).
     */
    DESCENDING,
    
    /**
     * Unspecified sort order. The default will be used.
     */
    SORT_ORDER_UNSPECIFIED;
    
    /**
     * Convert this DTO SortOrder to the protocol buffer SortOrder enum.
     *
     * @return The corresponding protocol buffer SortOrder enum value
     */
    public goodmem.v1.Common.SortOrder toProtoSortOrder() {
        return switch (this) {
            case ASCENDING -> goodmem.v1.Common.SortOrder.ASCENDING;
            case DESCENDING -> goodmem.v1.Common.SortOrder.DESCENDING;
            default -> goodmem.v1.Common.SortOrder.SORT_ORDER_UNSPECIFIED;
        };
    }
    
    /**
     * Parse a sort order string to a SortOrder enum value.
     *
     * @param sortOrderStr The sort order string, may be null
     * @return The corresponding SortOrder enum value, or SORT_ORDER_UNSPECIFIED if null or invalid
     */
    public static SortOrder fromString(String sortOrderStr) {
        if (sortOrderStr == null) {
            return SORT_ORDER_UNSPECIFIED;
        }
        
        return switch (sortOrderStr.toUpperCase()) {
            case "ASCENDING" -> ASCENDING;
            case "DESCENDING" -> DESCENDING;
            default -> SORT_ORDER_UNSPECIFIED;
        };
    }
}
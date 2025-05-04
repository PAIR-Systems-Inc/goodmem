package cmd

import (
	"fmt"
	"time"

	"github.com/google/uuid"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// uuidStringToBytes converts a string UUID to binary representation for protobuf
func uuidStringToBytes(s string) ([]byte, error) {
	id, err := uuid.Parse(s)
	if err != nil {
		return nil, fmt.Errorf("invalid UUID format: %w", err)
	}
	return id.MarshalBinary()
}

// uuidBytesToString converts binary UUID from protobuf to canonical string representation
func uuidBytesToString(b []byte) (string, error) {
	if len(b) != 16 {
		return "", fmt.Errorf("invalid UUID bytes: expected 16 bytes, got %d", len(b))
	}

	var id uuid.UUID
	err := id.UnmarshalBinary(b)
	if err != nil {
		return "", fmt.Errorf("failed to unmarshal UUID: %w", err)
	}

	return id.String(), nil
}

// formatTimestamp converts a protocol buffer timestamp to a formatted string
func formatTimestamp(ts *timestamppb.Timestamp) string {
	if ts == nil {
		return "N/A"
	}
	t := time.Unix(ts.Seconds, int64(ts.Nanos)).UTC()
	return t.Format(time.RFC3339)
}

// This function was causing issues with undefined types
// Commented out until properly imported
/*
// formatV1Timestamp formats a v1.Timestamp (generated protobuf) to a string
func formatV1Timestamp(ts *v1.Timestamp) string {
	if ts == nil {
		return "N/A"
	}
	t := time.Unix(ts.Seconds, int64(ts.Nanos)).UTC()
	return t.Format(time.RFC3339)
}
*/

// truncateString shortens a string if it's longer than the specified length
// and adds an ellipsis, otherwise returns the original string
func truncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}

// formatUUID formats binary UUID bytes to a string
func formatUUID(uuidBytes []byte) string {
	if len(uuidBytes) != 16 {
		return "invalid-uuid"
	}

	var id uuid.UUID
	if err := id.UnmarshalBinary(uuidBytes); err != nil {
		return "invalid-uuid"
	}

	return id.String()
}
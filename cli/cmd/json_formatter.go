package cmd

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// formatProtoMessageAsJSON converts a protocol buffer message to a REST-friendly JSON format.
// It handles special conversions like:
// - Binary UUIDs to string representation
// - Protobuf Timestamps to ISO 8601 format
// - Properly handles nested messages and repeated fields
func formatProtoMessageAsJSON(msg proto.Message) ([]byte, error) {
	// First convert to a generic JSON representation
	marshaler := protojson.MarshalOptions{
		EmitUnpopulated: false, // Skip empty fields
		UseProtoNames:   true,  // Use proto field names (we'll convert to camelCase later)
	}
	
	jsonBytes, err := marshaler.Marshal(msg)
	if err != nil {
		return nil, fmt.Errorf("error marshaling message to JSON: %w", err)
	}
	
	// Unmarshal into a map to process special fields
	var jsonMap map[string]interface{}
	if err := json.Unmarshal(jsonBytes, &jsonMap); err != nil {
		return nil, fmt.Errorf("error processing JSON structure: %w", err)
	}
	
	// Process the map to handle special types
	processJSONMap(jsonMap)
	
	// Re-marshal to get the final JSON
	return json.MarshalIndent(jsonMap, "", "  ")
}

// processJSONMap recursively processes a JSON map to transform special types
func processJSONMap(v interface{}) {
	switch m := v.(type) {
	case map[string]interface{}:
		// Create a new map with transformed keys
		transformedMap := make(map[string]interface{})
		
		for key, val := range m {
			// Transform key from snake_case to camelCase
			camelKey := snakeToCamelCase(key)
			
			// Check for UUID fields (based on naming convention and value type)
			if isUUIDField(key) {
				if strVal, ok := val.(string); ok {
					// Convert base64-encoded binary UUID to string UUID
					if uuidStr, err := convertBase64ToUUID(strVal); err == nil {
						transformedMap[camelKey] = uuidStr
					} else {
						transformedMap[camelKey] = val
					}
				} else {
					transformedMap[camelKey] = val
				}
			} else if key == "created_at" || key == "updated_at" || strings.HasSuffix(key, "_at") {
				// Handle timestamp fields
				if timeMap, ok := val.(map[string]interface{}); ok {
					if seconds, sok := timeMap["seconds"].(float64); sok {
						var nanos int64 = 0
						if nanosVal, nok := timeMap["nanos"].(float64); nok {
							nanos = int64(nanosVal)
						}
						// Convert to ISO 8601 format
						timestamp := time.Unix(int64(seconds), nanos).UTC().Format(time.RFC3339)
						transformedMap[camelKey] = timestamp
					} else {
						transformedMap[camelKey] = val
					}
				} else {
					transformedMap[camelKey] = val
				}
			} else {
				// Recursively process nested structures
				processJSONMap(val)
				transformedMap[camelKey] = val
			}
		}
		
		// Replace all keys in the original map
		for k := range m {
			delete(m, k)
		}
		for k, v := range transformedMap {
			m[k] = v
		}
		
	case []interface{}:
		// Process each element in arrays
		for i := range m {
			processJSONMap(m[i])
		}
	}
}

// snakeToCamelCase converts a snake_case string to camelCase
func snakeToCamelCase(snake string) string {
	// Special handling for "next_token"
	if snake == "next_token" {
		return "nextToken"
	}
	
	// For JSON output, we want to preserve "id" suffix instead of converting to "Id"
	if snake == "id" {
		return "id"
	}
	
	words := strings.Split(snake, "_")
	for i := 1; i < len(words); i++ {
		if len(words[i]) > 0 {
			words[i] = strings.ToUpper(words[i][:1]) + words[i][1:]
		}
	}
	
	result := strings.Join(words, "")
	
	// Special case for ID suffix - convert "Id" to "ID" for consistency
	result = strings.ReplaceAll(result, "Id", "ID")
	
	return result
}

// isUUIDField determines if a field likely contains a UUID based on naming convention
func isUUIDField(fieldName string) bool {
	return strings.HasSuffix(fieldName, "_id") || 
	       fieldName == "id" || 
	       strings.HasSuffix(fieldName, "Id")
}

// convertBase64ToUUID converts a base64-encoded binary UUID to a string UUID
func convertBase64ToUUID(base64Str string) (string, error) {
	// Decode base64 string to binary
	binUUID, err := base64.StdEncoding.DecodeString(base64Str)
	if err != nil {
		return "", fmt.Errorf("failed to decode base64: %w", err)
	}
	
	// Only proceed if we have exactly 16 bytes (UUID size)
	if len(binUUID) != 16 {
		return "", fmt.Errorf("invalid UUID size: expected 16 bytes, got %d", len(binUUID))
	}
	
	// Convert to UUID
	var id uuid.UUID
	if err := id.UnmarshalBinary(binUUID); err != nil {
		return "", fmt.Errorf("failed to unmarshal UUID: %w", err)
	}
	
	return id.String(), nil
}

// ConvertProtoTimestampToISO8601 converts a protobuf Timestamp to an ISO 8601 formatted string
func ConvertProtoTimestampToISO8601(ts *timestamppb.Timestamp) string {
	if ts == nil {
		return ""
	}
	return time.Unix(ts.Seconds, int64(ts.Nanos)).UTC().Format(time.RFC3339)
}
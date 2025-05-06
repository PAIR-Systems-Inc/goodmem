package com.goodmem.util;

import java.util.Map;

/**
 * Utility class for label-related operations.
 */
public final class LabelUtils {

  private LabelUtils() {
    // Utility class, no instances
  }
  
  /**
   * Checks if a set of labels matches the provided label selectors.
   * All selectors must match for the method to return true.
   * 
   * @param labels The labels to check
   * @param selectors The label selectors to match against
   * @return true if all selectors match, false otherwise
   */
  public static boolean matchesLabelSelectors(Map<String, String> labels, Map<String, String> selectors) {
    if (labels == null || selectors == null || selectors.isEmpty()) {
      return selectors == null || selectors.isEmpty();
    }
    
    for (Map.Entry<String, String> selector : selectors.entrySet()) {
      String key = selector.getKey();
      String value = selector.getValue();
      
      if (!labels.containsKey(key) || !labels.get(key).equals(value)) {
        return false;
      }
    }
    
    return true;
  }
}
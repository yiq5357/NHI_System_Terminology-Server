package com.hitstdio.fhir.server.util;

import java.util.*;

/**
 * Processes language preferences for ValueSet expansion.
 * Handles Accept-Language header parsing and language matching.
 */
public class LanguageProcessor {
    
    /**
     * Parses language preferences from Accept-Language header format
     * Example: "en-US,en;q=0.9,zh-TW;q=0.8,zh;q=0.7,*;q=0.1"
     */
    public List<LanguagePreference> parseLanguagePreferences(String langHeader) {
        if (langHeader == null || langHeader.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<LanguagePreference> preferences = new ArrayList<>();
        String[] parts = langHeader.split(",");
        
        for (String part : parts) {
            String[] subParts = part.trim().split(";");
            String lang = subParts[0].trim();
            double quality = 1.0; // Default quality value
            
            // Parse quality value if present
            if (subParts.length > 1) {
                for (int i = 1; i < subParts.length; i++) {
                    String param = subParts[i].trim();
                    if (param.toLowerCase().startsWith("q=")) {
                        try {
                            quality = Double.parseDouble(param.substring(2));
                        } catch (NumberFormatException e) {
                            // Ignore invalid quality values, use default
                        }
                    }
                }
            }
            
            preferences.add(new LanguagePreference(lang, quality));
        }
        
        // Sort by quality (highest first)
        Collections.sort(preferences);
        return preferences;
    }
    
    /**
     * Represents a language preference with quality value
     */
    public static class LanguagePreference implements Comparable<LanguagePreference> {
        public final String language;
        public final double quality;
        
        public LanguagePreference(String language, double quality) {
            this.language = language;
            this.quality = quality;
        }
        
        @Override
        public int compareTo(LanguagePreference other) {
            // Higher quality comes first
            return Double.compare(other.quality, this.quality);
        }
        
        @Override
        public String toString() {
            return language + ";q=" + quality;
        }
    }
}
package com.babeltech.babelkey;

import org.junit.Test;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * DictionaryLoaderTest
 *
 * Tests the JSON parsing logic used by SuggestionManager to load
 * key-value and prefix dictionaries. All parsing is exercised here
 * without any Android Context or file I/O — JSON strings are inlined.
 *
 * Mirrors loadKeyValueDictionaryFromJson() and loadPrefixDictionaryFromJson()
 * from SuggestionManager.
 */
public class DictionaryLoaderTest {

    // ── Helpers that mirror SuggestionManager parsing ────────────────────────

    /** Parses a flat key->value JSON object into the target map. */
    private void parseKV(String json, Map<String, String> target) throws Exception {
        target.clear();
        if (json == null || json.trim().isEmpty()) return;
        JSONObject o = new JSONObject(json);
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            String v = o.getString(k);
            if (k != null && !k.trim().isEmpty() && v != null && !v.trim().isEmpty())
                target.put(k.trim().toLowerCase(), v.trim());
        }
    }

    /** Parses a prefix->string[] JSON object into the target map. */
    private void parsePrefix(String json, Map<String, List<String>> target) throws Exception {
        target.clear();
        if (json == null || json.trim().isEmpty()) return;
        JSONObject o = new JSONObject(json);
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONArray arr = o.getJSONArray(k);
            List<String> vals = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String v = arr.optString(i, null);
                if (v != null && !v.trim().isEmpty()) vals.add(v.trim());
            }
            if (k != null && !k.trim().isEmpty() && !vals.isEmpty())
                target.put(k.trim().toLowerCase(), vals);
        }
    }

    // ── Key-value dictionary tests ────────────────────────────────────────────

    @Test
    public void kvParsing_correctMappingCount() throws Exception {
        String json = "{\"teh\":\"the\",\"helo\":\"hello\",\"recieve\":\"receive\"}";
        Map<String, String> dict = new HashMap<>();
        parseKV(json, dict);
        assertEquals(3, dict.size());
    }

    @Test
    public void kvParsing_correctValues() throws Exception {
        String json = "{\"teh\":\"the\",\"helo\":\"hello\"}";
        Map<String, String> dict = new HashMap<>();
        parseKV(json, dict);
        assertEquals("the",   dict.get("teh"));
        assertEquals("hello", dict.get("helo"));
    }

    @Test
    public void kvParsing_keysAreLowerCased() throws Exception {
        String json = "{\"TEH\":\"the\",\"Helo\":\"hello\"}";
        Map<String, String> dict = new HashMap<>();
        parseKV(json, dict);
        assertTrue("Key should be lowercase", dict.containsKey("teh"));
        assertTrue("Key should be lowercase", dict.containsKey("helo"));
        assertFalse(dict.containsKey("TEH"));
    }

    @Test
    public void kvParsing_emptyJsonProducesEmptyMap() throws Exception {
        Map<String, String> dict = new HashMap<>();
        parseKV("{}", dict);
        assertTrue(dict.isEmpty());
    }

    @Test
    public void kvParsing_nullJsonProducesEmptyMap() throws Exception {
        Map<String, String> dict = new HashMap<>();
        parseKV(null, dict);
        assertTrue(dict.isEmpty());
    }

    @Test
    public void kvParsing_emptyStringProducesEmptyMap() throws Exception {
        Map<String, String> dict = new HashMap<>();
        parseKV("   ", dict);
        assertTrue(dict.isEmpty());
    }

    @Test
    public void kvParsing_skipsBlankValues() throws Exception {
        String json = "{\"good\":\"great\",\"bad\":\"\",\"ok\":\"okay\"}";
        Map<String, String> dict = new HashMap<>();
        parseKV(json, dict);
        // "bad" has an empty value — should be skipped
        assertEquals(2, dict.size());
        assertFalse(dict.containsKey("bad"));
    }

    // ── Prefix dictionary tests ───────────────────────────────────────────────

    @Test
    public void prefixParsing_correctEntryCount() throws Exception {
        String json = "{\"he\":[\"hello\",\"hey\",\"help\"],\"wo\":[\"world\",\"wonder\"]}";
        Map<String, List<String>> dict = new HashMap<>();
        parsePrefix(json, dict);
        assertEquals(2, dict.size());
    }

    @Test
    public void prefixParsing_correctSuggestionsPerPrefix() throws Exception {
        String json = "{\"he\":[\"hello\",\"hey\",\"help\"]}";
        Map<String, List<String>> dict = new HashMap<>();
        parsePrefix(json, dict);
        List<String> suggestions = dict.get("he");
        assertNotNull(suggestions);
        assertEquals(3, suggestions.size());
        assertTrue(suggestions.contains("hello"));
        assertTrue(suggestions.contains("hey"));
        assertTrue(suggestions.contains("help"));
    }

    @Test
    public void prefixParsing_emptyArraySkipped() throws Exception {
        String json = "{\"he\":[\"hello\"],\"xy\":[]}";
        Map<String, List<String>> dict = new HashMap<>();
        parsePrefix(json, dict);
        // "xy" has an empty array — should be skipped
        assertEquals(1, dict.size());
        assertFalse(dict.containsKey("xy"));
    }

    @Test
    public void prefixParsing_keysAreLowerCased() throws Exception {
        String json = "{\"HE\":[\"hello\",\"hey\"]}";
        Map<String, List<String>> dict = new HashMap<>();
        parsePrefix(json, dict);
        assertTrue(dict.containsKey("he"));
        assertFalse(dict.containsKey("HE"));
    }

    @Test
    public void prefixParsing_emptyJsonProducesEmptyMap() throws Exception {
        Map<String, List<String>> dict = new HashMap<>();
        parsePrefix("{}", dict);
        assertTrue(dict.isEmpty());
    }

    @Test
    public void prefixParsing_nullJsonProducesEmptyMap() throws Exception {
        Map<String, List<String>> dict = new HashMap<>();
        parsePrefix(null, dict);
        assertTrue(dict.isEmpty());
    }
}
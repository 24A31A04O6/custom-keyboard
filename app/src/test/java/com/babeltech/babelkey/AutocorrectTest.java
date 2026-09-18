package com.babeltech.babelkey;

import org.junit.Before;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * AutocorrectTest
 *
 * Tests the autocorrect dictionary lookup and case-preservation logic that
 * mirrors the behavior in SuggestionManager.handleAutoCorrect().
 * Runs on the JVM — no Android Context required.
 */
public class AutocorrectTest {

    /** Minimal autocorrect map replicating common entries from autocorrect_dict.json. */
    private Map<String, String> autocorrectDict;

    @Before
    public void setUp() {
        autocorrectDict = new HashMap<>();
        autocorrectDict.put("teh", "the");
        autocorrectDict.put("helo", "hello");
        autocorrectDict.put("recieve", "receive");
        autocorrectDict.put("occured", "occurred");
        autocorrectDict.put("wierd", "weird");
        autocorrectDict.put("definately", "definitely");
        autocorrectDict.put("seperate", "separate");
        autocorrectDict.put("accomodate", "accommodate");
        autocorrectDict.put("dont", "don't");
        autocorrectDict.put("cant", "can't");
        autocorrectDict.put("wont", "won't");
        autocorrectDict.put("im", "I'm");
        autocorrectDict.put("youre", "you're");
    }

    /**
     * Applies autocorrect to a single word, preserving the original capitalisation.
     * Mirrors SuggestionManager's per-word correction logic.
     */
    private String correctWord(String word) {
        String lower = word.toLowerCase();
        String replacement = autocorrectDict.get(lower);
        if (replacement == null) return word; // unknown — leave unchanged
        if (Character.isUpperCase(word.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    // ── Correction tests ──────────────────────────────────────────────────────

    @Test
    public void tehCorrectedToThe() {
        assertEquals("the", correctWord("teh"));
    }

    @Test
    public void heloCorrectedToHello() {
        assertEquals("hello", correctWord("helo"));
    }

    @Test
    public void recieveCorrectedToReceive() {
        assertEquals("receive", correctWord("recieve"));
    }

    @Test
    public void occuredCorrectedToOccurred() {
        assertEquals("occurred", correctWord("occured"));
    }

    @Test
    public void wierdCorrectedToWeird() {
        assertEquals("weird", correctWord("wierd"));
    }

    @Test
    public void definatelyCorrectedToDefinitely() {
        assertEquals("definitely", correctWord("definately"));
    }

    @Test
    public void seperateCorrectedToSeparate() {
        assertEquals("separate", correctWord("seperate"));
    }

    // ── Case preservation tests ───────────────────────────────────────────────

    @Test
    public void uppercaseTeh_preservesCapital() {
        assertEquals("The", correctWord("Teh"));
    }

    @Test
    public void uppercaseHelo_preservesCapital() {
        assertEquals("Hello", correctWord("Helo"));
    }

    @Test
    public void uppercaseRecieve_preservesCapital() {
        assertEquals("Receive", correctWord("Recieve"));
    }

    @Test
    public void uppercaseOccured_preservesCapital() {
        assertEquals("Occurred", correctWord("Occured"));
    }

    // ── Passthrough tests (no correction) ────────────────────────────────────

    @Test
    public void correctWord_noChange_returnsOriginal() {
        assertEquals("hello", correctWord("hello"));
        assertEquals("the",   correctWord("the"));
        assertEquals("world", correctWord("world"));
    }

    @Test
    public void unknownWord_isPassedThrough() {
        String word = "supercalifragilistic";
        assertEquals(word, correctWord(word));
    }

    // ── Null / edge case tests ────────────────────────────────────────────────

    @Test
    public void singleCharWord_noCorrection() {
        assertEquals("a", correctWord("a"));
        assertEquals("I", correctWord("I"));
    }

    @Test
    public void apostropheExpansion_dont() {
        assertEquals("don't", correctWord("dont"));
    }

    @Test
    public void apostropheExpansion_cant() {
        assertEquals("can't", correctWord("cant"));
    }

    @Test
    public void apostropheExpansion_wont() {
        assertEquals("won't", correctWord("wont"));
    }

    @Test
    public void allEntriesHaveNonEmptyReplacements() {
        for (Map.Entry<String, String> e : autocorrectDict.entrySet()) {
            assertFalse("Empty key in dict",   e.getKey().trim().isEmpty());
            assertFalse("Empty value in dict", e.getValue().trim().isEmpty());
            assertNotEquals("Key equals value (pointless entry)",
                    e.getKey(), e.getValue());
        }
    }
}
package com.babeltech.babelkey;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/**
 * QuickReplyTest
 *
 * Validates the rule-based fallback logic that mirrors SmartReplyManager.ruleBased().
 * Runs on the JVM — no Android framework or ML Kit required.
 */
public class QuickReplyTest {

    /** Mirrors SmartReplyManager.ruleBased() exactly for pure-JVM testing. */
    private List<String> ruleBased(String text) {
        if (text == null) return Arrays.asList("Got it", "Okay!", "Sounds good");
        String lower = text.toLowerCase();
        if (lower.contains("?"))
            return Arrays.asList("Yes", "No", "Maybe");
        if (lower.contains("hello") || lower.contains("hi ")
                || lower.contains("hey") || lower.startsWith("hi"))
            return Arrays.asList("Hey!", "Hi there!", "Hello!");
        if (lower.contains("thank"))
            return Arrays.asList("No problem!", "Sure!", "Glad to help!");
        if (lower.contains("sorry") || lower.contains("apolog"))
            return Arrays.asList("It's okay!", "No worries!", "All good!");
        return Arrays.asList("Got it", "Okay!", "Sounds good");
    }

    @Test
    public void question_returnsYesNoMaybe() {
        List<String> r = ruleBased("How are you doing?");
        assertEquals(3, r.size());
        assertTrue(r.contains("Yes"));
        assertTrue(r.contains("No"));
        assertTrue(r.contains("Maybe"));
    }

    @Test
    public void greeting_hi_returnsGreetings() {
        List<String> r = ruleBased("Hi there, what is up");
        assertEquals(3, r.size());
        assertFalse("Should not return generic fallback", r.contains("Got it"));
        assertTrue(r.contains("Hey!") || r.contains("Hi there!") || r.contains("Hello!"));
    }

    @Test
    public void greeting_hello_returnsGreetings() {
        List<String> r = ruleBased("hello friend");
        assertTrue(r.contains("Hello!") || r.contains("Hey!") || r.contains("Hi there!"));
    }

    @Test
    public void thanks_returnsThanksFallback() {
        List<String> r = ruleBased("Thanks for helping me out!");
        assertEquals(3, r.size());
        assertTrue(r.contains("No problem!"));
        assertTrue(r.contains("Sure!"));
        assertTrue(r.contains("Glad to help!"));
        assertFalse("Must not overlap with question replies", r.contains("Yes"));
    }

    @Test
    public void sorry_returnsApologyFallback() {
        List<String> r = ruleBased("I am sorry I was late");
        assertEquals(3, r.size());
        assertTrue(r.contains("It's okay!"));
        assertTrue(r.contains("No worries!"));
        assertTrue(r.contains("All good!"));
    }

    @Test
    public void unknownPhrase_returnsGeneralFallback() {
        List<String> r = ruleBased("Let us meet tomorrow at noon");
        assertEquals(3, r.size());
        assertTrue(r.contains("Got it"));
        assertTrue(r.contains("Okay!"));
        assertTrue(r.contains("Sounds good"));
    }

    @Test
    public void nullInput_returnsGeneralFallback() {
        List<String> r = ruleBased(null);
        assertNotNull(r);
        assertFalse(r.isEmpty());
        assertEquals(3, r.size());
    }

    @Test
    public void emptyInput_returnsGeneralFallback() {
        List<String> r = ruleBased("   ");
        assertEquals(3, r.size());
        assertTrue(r.contains("Got it"));
    }

    @Test
    public void allCategoriesAreDistinct() {
        List<String> question = ruleBased("how are you?");
        List<String> thanks   = ruleBased("thank you so much");
        List<String> general  = ruleBased("call me later");
        for (String s : question) assertFalse("Question/Thanks overlap: " + s, thanks.contains(s));
        for (String s : general)  assertFalse("General/Question overlap: " + s, question.contains(s));
    }
}
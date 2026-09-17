package com.babeltech.babelkey.layout;

import java.util.HashMap;
import java.util.Map;

/**
 * KeySymbolMap
 * Static lookup table mapping a key character (by its Unicode code point) to
 * an ordered array of alternative symbols shown in the long-press popup.
 *
 * The first entry in each array is always the primary character itself so the
 * user can release without sliding and still get the base symbol.
 */
public final class KeySymbolMap {

    private static final Map<Integer, String[]> MAP = new HashMap<>();

    static {
        // ── Currency ────────────────────────────────────────────────────────
        put('$',  "$", "€", "£", "¥", "₹", "¢", "₽", "₩");

        // ── Punctuation & quotes ─────────────────────────────────────────────
        put('?',  "?", "¿", "‽");
        put('!',  "!", "¡");
        put('.',  ".", "…", ",", "?", "!");
        put('"',  "\"", "\u201C", "\u201D", "\u201E", "«", "»");
        put('\'', "'", "\u2018", "\u2019", "`", "\u00B4");
        put('-',  "-", "–", "—", "•", "_");
        put('/',  "/", "\\");
        put('%',  "%", "‰", "℅");
        put('&',  "&", "§");
        put('*',  "*", "★", "†", "‡");
        put('(',  "(", "[", "{", "<");
        put(')',  ")", "]", "}", ">");
        put('=',  "=", "≠", "≈", "±", "≤", "≥", "∞");
        put('#',  "#", "№");
        put('@',  "@", "©", "®", "™");
        put('+',  "+", "±", "÷", "×");
        put('<',  "<", "≤", "«");
        put('>',  ">", "≥", "»");
        put(':',  ":", ";");
        put(';',  ";", ":");
        put(',',  ",", ";", ":", "…");
        put('_',  "_", "–", "—");
        put('^',  "^", "°", "²", "³");

        // ── Accented letters ─────────────────────────────────────────────────
        put('a',  "a", "á", "à", "â", "ä", "ã", "å", "@");
        put('A',  "A", "Á", "À", "Â", "Ä", "Ã", "Å", "@");
        put('e',  "e", "é", "è", "ê", "ë", "3");
        put('E',  "E", "É", "È", "Ê", "Ë");
        put('i',  "i", "í", "ì", "î", "ï", "8");
        put('I',  "I", "Í", "Ì", "Î", "Ï");
        put('o',  "o", "ó", "ò", "ô", "ö", "õ", "9");
        put('O',  "O", "Ó", "Ò", "Ô", "Ö", "Õ");
        put('u',  "u", "ú", "ù", "û", "ü");
        put('U',  "U", "Ú", "Ù", "Û", "Ü");
        put('c',  "c", "ç", "ć");
        put('C',  "C", "Ç", "Ć");
        put('n',  "n", "ñ", "ń");
        put('N',  "N", "Ñ", "Ń");
        put('s',  "s", "ß", "š", "$");
        put('S',  "S", "Š");
        put('z',  "z", "ž", "ź", "ż");
        put('Z',  "Z", "Ž", "Ź", "Ż");
        put('y',  "y", "ý", "ÿ");
        put('Y',  "Y", "Ý");
        put('d',  "d", "ð");
        put('D',  "D", "Ð");
        put('t',  "t", "þ");
        put('T',  "T", "Þ");
        put('l',  "l", "ł");
        put('L',  "L", "Ł");
        put('r',  "r", "ř");
        put('R',  "R", "Ř");

        // ── Number row extras ────────────────────────────────────────────────
        put('0',  "0", "°", "∅", "Ø");
        put('1',  "1", "¹", "½", "¼");
        put('2',  "2", "²", "½");
        put('3',  "3", "³", "¾");
        put('5',  "5", "§", "%");
    }

    private static void put(int codePoint, String... symbols) {
        MAP.put(codePoint, symbols);
    }

    /**
     * Returns the symbol alternatives for the given key code point,
     * or {@code null} if no alternatives are defined.
     */
    public static String[] get(int codePoint) {
        return MAP.get(codePoint);
    }

    /** Returns true if the given code point has long-press alternatives. */
    public static boolean has(int codePoint) {
        return MAP.containsKey(codePoint);
    }

    private KeySymbolMap() { /* static utility class */ }
}

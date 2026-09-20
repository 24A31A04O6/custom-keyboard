import autocorrectDictRaw from '../data/autocorrect_dict.json';
import emojiSuggestionsRaw from '../data/emoji_suggestions.json';
import engDictRaw from '../data/eng_dict.json';
import suggestionsDictRaw from '../data/suggestions_dict.json';
import telEngDictRaw from '../data/tel_eng_dict.json';
import translationsRaw from '../data/translations.json';

const AUTOCORRECT_DICT: Record<string, string> = autocorrectDictRaw as Record<string, string>;
const EMOJI_SUGGESTIONS: Record<string, string> = emojiSuggestionsRaw as Record<string, string>;
const ENG_DICT: Record<string, string[]> = engDictRaw as Record<string, string[]>;
const SUGGESTIONS_DICT: Record<string, string[]> = suggestionsDictRaw as Record<string, string[]>;
const TEL_ENG_DICT: Record<string, string[]> = telEngDictRaw as Record<string, string[]>;
const TRANSLATIONS: Record<string, string> = translationsRaw as Record<string, string>;

// Grammar dictionary directly from SuggestionManager.java
export const GRAMMAR_DICT: Record<string, string> = {
  "nenu veltru": "nenu veltharu",
  "nenu chustru": "nenu chustharu",
  "nenu chestru": "nenu chestharu",
  "nenu vastru": "nenu vastharu",
  "nenu istru": "nenu istharu",
  "meeru veltru": "meeru veltharu",
  "meeru chustru": "meeru chustharu",
  "vaadu veltru": "vaadu velthadu",
  "vaadu chustru": "vaadu chustadu",
  "aame veltru": "aame velthundi",
  "na peru": "naa peru",
  "na illu": "naa illu",
  "na pani": "naa pani",
  "na naanna": "naa naanna",
};

/**
 * Get suggestions for the current word:
 * 1. Emoji predictions (e.g. 'happy' -> '😊 😄 🎉')
 * 2. Telugu-English (Romanized Telugu) word suggestions (e.g. 'nen' -> 'nenu', 'nuv' -> 'nuvvu', 'ela' -> 'ela')
 * 3. English word completions (e.g. 'hel' -> 'hello', 'help', 'tha' -> 'thanks', 'that')
 * 4. Personal dictionary matches
 *
 * NOTE: Both Tel-Eng and English words are suggested in clean Latin/English letters (no Telugu script characters).
 */
export function getSuggestions(
  currentWord: string,
  personalDict: string[] = []
): string[] {
  if (!currentWord || currentWord.trim().length === 0) return [];

  const prefix = currentWord.trim().toLowerCase();
  const results: string[] = [];

  // 1. Check Emoji suggestions
  const emojiStr = EMOJI_SUGGESTIONS[prefix];
  if (emojiStr) {
    const emojis = emojiStr.split(/\s+/).filter(Boolean);
    results.unshift(...emojis);
  }

  // 2. Check Telugu-English (Romanized Telugu) dictionary
  const telEngMatches = TEL_ENG_DICT[prefix];
  if (telEngMatches && telEngMatches.length > 0) {
    for (const w of telEngMatches) {
      if (!results.includes(w)) {
        results.push(w);
        if (results.length >= 7) break;
      }
    }
  }

  // 3. Check English prefix dictionary (comprehensive list)
  const engMatches = ENG_DICT[prefix];
  if (engMatches && engMatches.length > 0) {
    for (const w of engMatches) {
      if (!results.includes(w)) {
        results.push(w);
        if (results.length >= 9) break;
      }
    }
  }

  // Fallback check against initial small suggestions dictionary
  for (const [key, words] of Object.entries(SUGGESTIONS_DICT)) {
    if (prefix.startsWith(key) || key.startsWith(prefix)) {
      for (const w of words) {
        if (w.startsWith(prefix) && !results.includes(w)) {
          results.push(w);
          if (results.length >= 9) break;
        }
      }
    }
    if (results.length >= 9) break;
  }

  // 4. Personal dictionary matches
  for (const w of personalDict) {
    if (w.toLowerCase().startsWith(prefix) && !results.includes(w)) {
      results.push(w);
    }
  }

  return results.slice(0, 8);
}

/**
 * Check text for grammar fixes from GRAMMAR_DICT.
 */
export function checkGrammar(text: string): { original: string; suggestion: string } | null {
  if (!text) return null;
  const lower = text.toLowerCase();
  for (const [key, val] of Object.entries(GRAMMAR_DICT)) {
    if (lower.includes(key)) {
      return { original: key, suggestion: val };
    }
  }
  return null;
}

/**
 * Smart replies based on conversation context (matching SmartReplyManager.java)
 */
export function getSmartReplies(contextText: string): string[] {
  if (!contextText || contextText.trim().length === 0) {
    return ["Got it", "Okay!", "Sounds good"];
  }

  const lower = contextText.toLowerCase();
  if (lower.includes('?')) {
    return ["Yes", "No", "Maybe"];
  }
  if (lower.includes('hello') || lower.includes('hi ') || lower.includes('hey') || lower.startsWith('hi')) {
    return ["Hey!", "Hi there!", "Hello!"];
  }
  if (lower.includes('thank')) {
    return ["No problem!", "Sure!", "Glad to help!"];
  }
  if (lower.includes('sorry') || lower.includes('apolog')) {
    return ["It's okay!", "No worries!", "All good!"];
  }
  return ["Got it", "Okay!", "Sounds good", "Will do!"];
}

/**
 * Auto-correct a single word
 */
export function getAutoCorrection(
  word: string,
  learnedWords: Set<string> = new Set(),
  personalDict: string[] = []
): string | null {
  const wl = word.toLowerCase();
  if (learnedWords.has(wl) || personalDict.some(p => p.toLowerCase() === wl)) {
    return null;
  }
  const rep = AUTOCORRECT_DICT[wl];
  if (!rep) return null;

  // Preserve capitalization
  if (word[0] === word[0].toUpperCase()) {
    return rep.charAt(0).toUpperCase() + rep.slice(1);
  }
  return rep;
}

/**
 * Auto-correct full sentence
 */
export function autoCorrectSentence(
  sentence: string,
  learnedWords: Set<string> = new Set(),
  personalDict: string[] = []
): { corrected: string; changedWords: string[] } {
  const words = sentence.split(/(\s+|[.,!?;:])/);
  const changedWords: string[] = [];

  const correctedWords = words.map(chunk => {
    if (/^[a-zA-Z]+$/.test(chunk)) {
      const fix = getAutoCorrection(chunk, learnedWords, personalDict);
      if (fix && fix.toLowerCase() !== chunk.toLowerCase()) {
        changedWords.push(chunk);
        return fix;
      }
    }
    return chunk;
  });

  return {
    corrected: correctedWords.join(''),
    changedWords
  };
}

// Precompute reverse translation dictionary and phrase lists
const REVERSE_TRANSLATIONS: Record<string, string> = {};
for (const [en, te] of Object.entries(TRANSLATIONS)) {
  REVERSE_TRANSLATIONS[te.toLowerCase()] = en;
}
const EN_PHRASES = Object.keys(TRANSLATIONS)
  .filter((k) => k.includes(' '))
  .sort((a, b) => b.length - a.length);
const TE_PHRASES = Object.values(TRANSLATIONS)
  .filter((v) => v.includes(' '))
  .map((v) => v.toLowerCase())
  .sort((a, b) => b.length - a.length);

/**
 * Translate sentence between Telugu and English using translations.json
 * Supports both full multi-word conversational phrases (e.g. "how are you" <-> "ela unnaru")
 * and individual words (e.g. "nenu" <-> "i", "namaskaram" <-> "hello").
 */
export function translateText(text: string): { translated: string; changed: boolean } {
  if (!text || text.trim().length === 0) {
    return { translated: text, changed: false };
  }

  let result = text;
  let changed = false;

  // 1. Direct whole-text phrase lookup
  const trimmed = text.trim();
  const lowerTrimmed = trimmed.toLowerCase();
  if (TRANSLATIONS[lowerTrimmed]) {
    const target = TRANSLATIONS[lowerTrimmed];
    const capitalized = trimmed[0] === trimmed[0].toUpperCase() ? target.charAt(0).toUpperCase() + target.slice(1) : target;
    return { translated: capitalized, changed: true };
  }
  if (REVERSE_TRANSLATIONS[lowerTrimmed]) {
    const target = REVERSE_TRANSLATIONS[lowerTrimmed];
    const capitalized = trimmed[0] === trimmed[0].toUpperCase() ? target.charAt(0).toUpperCase() + target.slice(1) : target;
    return { translated: capitalized, changed: true };
  }

  // 2. Multi-word phrase replacements within text
  for (const phrase of EN_PHRASES) {
    const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`\\b${escaped}\\b`, 'gi');
    if (regex.test(result)) {
      result = result.replace(regex, (match) => {
        changed = true;
        const rep = TRANSLATIONS[phrase];
        return match[0] === match[0].toUpperCase() ? rep.charAt(0).toUpperCase() + rep.slice(1) : rep;
      });
    }
  }

  for (const phrase of TE_PHRASES) {
    const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`\\b${escaped}\\b`, 'gi');
    if (regex.test(result)) {
      result = result.replace(regex, (match) => {
        changed = true;
        const rep = REVERSE_TRANSLATIONS[phrase];
        return match[0] === match[0].toUpperCase() ? rep.charAt(0).toUpperCase() + rep.slice(1) : rep;
      });
    }
  }

  // 3. Individual word token translation
  const tokens = result.split(/(\s+|[.,!?;:])/);
  const translatedTokens = tokens.map((token) => {
    if (/^[a-zA-Z]+$/.test(token)) {
      const lower = token.toLowerCase();
      // Try English -> Telugu first, then Telugu -> English
      const target = TRANSLATIONS[lower] || REVERSE_TRANSLATIONS[lower];

      if (target) {
        changed = true;
        if (token[0] === token[0].toUpperCase()) {
          return target.charAt(0).toUpperCase() + target.slice(1);
        }
        return target;
      }
    }
    return token;
  });

  return {
    translated: translatedTokens.join(''),
    changed,
  };
}

/**
 * Check if text contains an OTP code (4-8 digits)
 */
export function detectOtp(text: string): string | null {
  const match = text.match(/\b(\d{4,8})\b/);
  return match ? match[1] : null;
}

/**
 * Match a text shortcut
 */
export function checkShortcut(word: string, shortcuts: Record<string, string>): string | null {
  if (!word) return null;
  const clean = word.trim().toLowerCase();
  return shortcuts[clean] || null;
}

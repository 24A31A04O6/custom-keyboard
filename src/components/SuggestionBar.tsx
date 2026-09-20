import React from 'react';
import { ThemeId, EditorInputType } from '../types';
import { Sparkles, Check, KeyRound, ShieldAlert } from 'lucide-react';

interface SuggestionBarProps {
  themeId: ThemeId;
  suggestions: string[];
  smartReplies: string[];
  grammarError: { original: string; suggestion: string } | null;
  otpDetected: string | null;
  onSelectSuggestion: (word: string) => void;
  onApplyGrammarFix: (fix: { original: string; suggestion: string }) => void;
  onPasteOtp: (otp: string) => void;
  onSelectSmartReply: (reply: string) => void;
  currentWord: string;
  editorInputType?: EditorInputType;
  isSymbolOrNumberLayer?: boolean;
}

export const SuggestionBar: React.FC<SuggestionBarProps> = ({
  themeId,
  suggestions,
  smartReplies,
  grammarError,
  otpDetected,
  onSelectSuggestion,
  onApplyGrammarFix,
  onPasteOtp,
  onSelectSmartReply,
  currentWord,
  editorInputType = 'text',
  isSymbolOrNumberLayer = false,
}) => {
  const isLight = themeId === ThemeId.LIGHT;
  const isPrivacyMode = editorInputType === 'password' || editorInputType === 'visible_password';

  return (
    <div
      id="suggestion_container"
      className={`w-full flex flex-col border-b transition-colors ${
        isLight
          ? 'bg-neutral-100/95 border-neutral-300 text-neutral-800'
          : themeId === ThemeId.BLUE
          ? 'bg-[#102E50]/95 border-[#1a3e68] text-blue-100'
          : themeId === ThemeId.GREEN
          ? 'bg-[#103818]/95 border-[#1a4a22] text-green-100'
          : themeId === ThemeId.AMOLED || themeId === ThemeId.BLACK
          ? 'bg-black border-neutral-800 text-neutral-200'
          : 'bg-[#23282D]/95 border-[#2c333a] text-neutral-200'
      }`}
    >
      {/* OTP Detection Banner if present */}
      {otpDetected && (
        <div
          id="otp_banner"
          onClick={() => onPasteOtp(otpDetected)}
          className="flex items-center justify-between px-3 py-1.5 bg-amber-500/20 text-amber-500 hover:bg-amber-500/30 cursor-pointer border-b border-amber-500/30 transition-colors text-xs"
        >
          <div className="flex items-center space-x-1.5 font-medium">
            <KeyRound size={14} />
            <span>OTP Detected: <strong className="font-mono text-sm tracking-wider">{otpDetected}</strong></span>
          </div>
          <span className="text-[11px] underline">Tap to insert</span>
        </div>
      )}

      {/* Grammar Correction Banner if detected */}
      {grammarError && (
        <div
          id="grammar_banner"
          onClick={() => onApplyGrammarFix(grammarError)}
          className="flex items-center justify-between px-3 py-1 bg-emerald-500/15 text-emerald-500 hover:bg-emerald-500/25 cursor-pointer border-b border-emerald-500/20 transition-colors text-xs"
        >
          <div className="flex items-center space-x-1.5">
            <Check size={13} />
            <span>Telugu Grammar fix: <span className="line-through opacity-75">{grammarError.original}</span> → <strong>{grammarError.suggestion}</strong></span>
          </div>
          <span className="text-[11px] font-medium bg-emerald-500 text-white dark:text-neutral-900 px-2 py-0.5 rounded-full">Apply</span>
        </div>
      )}

      {/* Primary Suggestion Bar */}
      <div
        id="suggestion_scroll"
        className="h-11 px-2.5 flex items-center space-x-2 overflow-x-auto no-scrollbar scroll-smooth"
      >
        {isPrivacyMode ? (
          <div
            id="privacy_protocol_strip"
            className="w-full h-full flex items-center justify-between px-1 select-none"
          >
            <div className="flex items-center space-x-2 text-amber-600 dark:text-amber-400 font-medium text-xs">
              <ShieldAlert size={15} className="shrink-0 text-amber-500" />
              <span className="font-semibold tracking-tight">Privacy Protocol Active</span>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-700 dark:text-amber-300 font-bold border border-amber-500/30">
                {editorInputType === 'password' ? 'TYPE_TEXT_VARIATION_PASSWORD' : 'TYPE_TEXT_VARIATION_VISIBLE_PASSWORD'}
              </span>
            </div>
            <span className="text-[11px] text-neutral-500 dark:text-neutral-400 hidden sm:inline font-normal">
              Dictionary learning & auto-correct bypassed
            </span>
          </div>
        ) : isSymbolOrNumberLayer ? (
          <div
            id="symbol_layer_indicator"
            className="w-full h-full flex items-center justify-between px-1 select-none"
          >
            <span className="text-xs text-neutral-500 dark:text-neutral-400 font-medium tracking-tight">
              Autocorrect & prediction paused on symbol/number layer
            </span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-neutral-200/80 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 border border-neutral-300 dark:border-neutral-700 font-medium">
              123 & Symbols
            </span>
          </div>
        ) : suggestions.length > 0 ? (
          suggestions.map((item, index) => {
            const isFirst = index === 0;
            return (
              <button
                key={`${item}-${index}`}
                id={`suggestion_chip_${index}`}
                onClick={() => onSelectSuggestion(item)}
                className={`h-8 px-3.5 rounded-full text-sm font-medium flex items-center justify-center whitespace-nowrap transition-all shrink-0 ${
                  isFirst
                    ? isLight
                      ? 'bg-neutral-300/90 text-neutral-900 font-semibold shadow-xs'
                      : 'bg-white/25 text-white font-semibold shadow-xs'
                    : isLight
                    ? 'hover:bg-neutral-200 text-neutral-800 bg-neutral-200/50'
                    : 'hover:bg-white/10 text-neutral-200 bg-white/5'
                }`}
              >
                {item}
              </button>
            );
          })
        ) : (
          /* When no word is being actively typed, show Smart Reply chips */
          smartReplies.length > 0 && (
            <div id="ai_replies_strip" className="flex items-center space-x-1.5 py-0.5">
              <span className="text-[10px] uppercase font-semibold text-neutral-400 flex items-center gap-1 pl-1 pr-1 shrink-0">
                <Sparkles size={11} className="text-amber-400" />
                Quick Reply
              </span>
              {smartReplies.map((reply, idx) => (
                <button
                  key={`reply-${idx}`}
                  onClick={() => onSelectSmartReply(reply)}
                  className={`h-8 px-3 rounded-full text-xs font-medium transition-colors shrink-0 ${
                    isLight
                      ? 'bg-blue-100 hover:bg-blue-200 text-blue-800'
                      : 'bg-blue-900/40 hover:bg-blue-800/60 text-blue-200 border border-blue-700/50'
                  }`}
                >
                  {reply}
                </button>
              ))}
            </div>
          )
        )}
      </div>
    </div>
  );
};

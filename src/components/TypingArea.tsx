import React, { useState } from 'react';
import { EditorInputType, ImeAction } from '../types';
import {
  Copy,
  Trash2,
  Check,
  MessageSquare,
  Sparkles,
  RefreshCw,
  RotateCcw,
  Eye,
  EyeOff,
  Search,
  Send,
  ArrowRight,
  CornerDownRight,
  CornerDownLeft,
  Lock,
  Hash,
  Mail,
  FileText,
  Download,
} from 'lucide-react';

interface TypingAreaProps {
  text: string;
  cursorPos: number;
  onTextChange: (newText: string, newPos: number) => void;
  incomingMessage: string;
  onCycleIncomingMessage: () => void;
  onQuickSampleInsert: (sample: string) => void;
  historyCount: number;
  onUndo: () => void;
  onClearHistory: () => void;
  historyClearedNotice?: boolean;
  editorInputType: EditorInputType;
  onChangeEditorInputType: (type: EditorInputType) => void;
  imeAction: ImeAction;
  onChangeImeAction: (action: ImeAction) => void;
  isMultiLine?: boolean;
  onChangeIsMultiLine?: (multiLine: boolean) => void;
  lastImeActionFeedback?: string | null;
  onFocus?: () => void;
}

export const SAMPLE_PROMPTS = [
  "Hey! Are you free for lunch today?",
  "Hello, how are you doing?",
  "Thank you for helping me out with the project!",
  "I am so sorry for being late to the meeting!",
  "Did you submit the report?",
  "Where are you right now?",
];

export const TypingArea: React.FC<TypingAreaProps> = ({
  text,
  cursorPos,
  onTextChange,
  incomingMessage,
  onCycleIncomingMessage,
  onQuickSampleInsert,
  historyCount,
  onUndo,
  onClearHistory,
  historyClearedNotice,
  editorInputType,
  onChangeEditorInputType,
  imeAction,
  onChangeImeAction,
  isMultiLine = false,
  onChangeIsMultiLine,
  lastImeActionFeedback,
  onFocus,
}) => {
  const [copied, setCopied] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleCopy = () => {
    if (!text) return;
    navigator.clipboard?.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleClear = () => {
    onTextChange('', 0);
  };

  // Determine displayed text (password masking)
  const isMaskedPassword = editorInputType === 'password';
  const isVisiblePassword = editorInputType === 'visible_password';
  const isStrictPrivacy = isMaskedPassword || isVisiblePassword;
  const displayedText = isMaskedPassword && !showPassword ? '•'.repeat(text.length) : text;

  return (
    <div id="typing_area_container" className="w-full max-w-2xl mx-auto flex flex-col p-3 space-y-3">
      {/* IME / InputConnection Testing Bar */}
      <div className="flex flex-col space-y-2 p-3 rounded-2xl bg-white/80 dark:bg-[#1A2026]/90 border border-neutral-200/80 dark:border-neutral-800 shadow-xs backdrop-blur-xs">
        <div className="flex items-center justify-between flex-wrap gap-2 text-xs">
          {/* InputType Selector (EditorInfo.inputType) */}
          <div className="flex items-center space-x-1.5 flex-wrap gap-y-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
              InputType:
            </span>
            <button
              type="button"
              onClick={() => onChangeEditorInputType('text')}
              title="TYPE_CLASS_TEXT: Standard alphanumeric layout"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'text'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <FileText size={12} />
              <span>Normal</span>
            </button>

            <button
              type="button"
              onClick={() => onChangeEditorInputType('phone')}
              title="TYPE_CLASS_PHONE: Automatically opens dedicated phone numpad layout"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'phone'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <Hash size={12} />
              <span>Phone</span>
            </button>

            <button
              type="button"
              onClick={() => onChangeEditorInputType('number')}
              title="TYPE_CLASS_NUMBER: Automatically opens dedicated numeric numpad layout"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'number'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <Hash size={12} />
              <span>Number</span>
            </button>

            <button
              type="button"
              onClick={() => onChangeEditorInputType('password')}
              title="TYPE_TEXT_VARIATION_PASSWORD: Masks characters, strictly disables predictive dictionary & learning"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'password'
                  ? 'bg-amber-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <Lock size={12} />
              <span>Password</span>
            </button>

            <button
              type="button"
              onClick={() => onChangeEditorInputType('visible_password')}
              title="TYPE_TEXT_VARIATION_VISIBLE_PASSWORD: Keeps text visible but strictly bypasses predictive dictionary & learning"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'visible_password'
                  ? 'bg-amber-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <Eye size={12} />
              <span>Visible Pwd</span>
            </button>

            <button
              type="button"
              onClick={() => {
                onChangeEditorInputType('search');
                onChangeImeAction('IME_ACTION_SEARCH');
                if (onChangeIsMultiLine) onChangeIsMultiLine(false);
              }}
              title="TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_FILTER: Search field automatically selects Search action"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'search'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <Search size={12} />
              <span>Search</span>
            </button>

            <button
              type="button"
              onClick={() => onChangeEditorInputType('email')}
              title="TYPE_TEXT_VARIATION_EMAIL_ADDRESS"
              className={`px-2 py-0.5 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-colors ${
                editorInputType === 'email'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200'
              }`}
            >
              <Mail size={12} />
              <span>Email</span>
            </button>
          </div>

          {/* IME Action Selector (EditorInfo.imeOptions) */}
          <div className="flex items-center space-x-1.5 flex-wrap gap-y-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
              Action:
            </span>
            {[
              { id: 'IME_ACTION_SEND' as ImeAction, altId: 'actionSend', label: 'Send', icon: Send, color: 'bg-emerald-600' },
              { id: 'IME_ACTION_SEARCH' as ImeAction, altId: 'actionSearch', label: 'Search', icon: Search, color: 'bg-blue-600' },
              { id: 'IME_ACTION_GO' as ImeAction, altId: 'actionGo', label: 'Go', icon: ArrowRight, color: 'bg-indigo-600' },
              { id: 'IME_ACTION_NEXT' as ImeAction, altId: 'actionNext', label: 'Next', icon: CornerDownRight, color: 'bg-sky-600' },
              { id: 'IME_ACTION_DONE' as ImeAction, altId: 'actionDone', label: 'Done', icon: Check, color: 'bg-teal-600' },
              { id: 'IME_ACTION_UNSPECIFIED' as ImeAction, altId: 'actionUnspecified', label: 'Unspecified', icon: CornerDownLeft, color: 'bg-neutral-700' },
              { id: 'IME_ACTION_NONE' as ImeAction, altId: 'actionNone', label: 'Return', icon: CornerDownLeft, color: 'bg-neutral-700' },
            ].map(({ id, altId, label, icon: ActionIcon, color }) => {
              const isSelected = imeAction === id || imeAction === altId;
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => onChangeImeAction(id)}
                  className={`px-2 py-0.5 rounded-md text-[11px] font-medium flex items-center space-x-1 transition-all ${
                    isSelected
                      ? `${color} text-white font-bold shadow-xs scale-105`
                      : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-400 hover:bg-neutral-200 dark:hover:bg-neutral-700'
                  }`}
                >
                  <ActionIcon size={11} className={isSelected ? 'stroke-[2.8]' : 'stroke-2'} />
                  <span>{label}</span>
                </button>
              );
            })}

            {/* Multi-line toggle */}
            {onChangeIsMultiLine && (
              <button
                type="button"
                onClick={() => onChangeIsMultiLine(!isMultiLine)}
                title="TYPE_TEXT_FLAG_MULTI_LINE: Forces newline Return key regardless of requested action"
                className={`px-2 py-0.5 rounded-md text-[11px] font-medium flex items-center space-x-1 transition-all ${
                  isMultiLine
                    ? 'bg-amber-600 text-white font-bold shadow-xs'
                    : 'border border-dashed border-neutral-300 dark:border-neutral-700 text-neutral-600 dark:text-neutral-400 hover:bg-neutral-100 dark:hover:bg-neutral-800'
                }`}
              >
                <span>Multiline {isMultiLine ? 'ON' : 'OFF'}</span>
              </button>
            )}
          </div>
        </div>

        {/* Quick App Scenario Presets (Gboard Parity Demonstration) */}
        <div className="flex items-center space-x-1.5 flex-wrap gap-y-1 text-[11px] pt-1 border-t border-neutral-200/60 dark:border-neutral-800/60">
          <span className="text-[10px] font-bold uppercase tracking-wider text-neutral-400 dark:text-neutral-500">
            Presets:
          </span>
          <button
            type="button"
            onClick={() => {
              onChangeEditorInputType('text');
              onChangeImeAction('IME_ACTION_SEND');
              if (onChangeIsMultiLine) onChangeIsMultiLine(false);
            }}
            title="WhatsApp send mode: Enter key renders as green 'Send' and dispatches message"
            className="px-2 py-0.5 rounded-md bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/50 hover:bg-emerald-100 font-medium transition-colors"
          >
            WhatsApp (Send)
          </button>
          <button
            type="button"
            onClick={() => {
              onChangeEditorInputType('text');
              onChangeImeAction('IME_ACTION_UNSPECIFIED');
              if (onChangeIsMultiLine) onChangeIsMultiLine(true);
            }}
            title="WhatsApp multi-line mode: Enter key renders as standard Return/newline"
            className="px-2 py-0.5 rounded-md bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-200 font-medium transition-colors"
          >
            WhatsApp (Next Line)
          </button>
          <button
            type="button"
            onClick={() => {
              onChangeEditorInputType('search');
              onChangeImeAction('IME_ACTION_SEARCH');
              if (onChangeIsMultiLine) onChangeIsMultiLine(false);
            }}
            title="Search field: Enter key renders as blue 'Search' with magnifying glass and executes query"
            className="px-2 py-0.5 rounded-md bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800/50 hover:bg-blue-100 font-medium transition-colors"
          >
            Browser Search
          </button>
          <button
            type="button"
            onClick={() => {
              onChangeEditorInputType('text');
              onChangeImeAction('IME_ACTION_GO');
              if (onChangeIsMultiLine) onChangeIsMultiLine(false);
            }}
            title="URL bar: Enter key renders as 'Go' and performs navigation"
            className="px-2 py-0.5 rounded-md bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800/50 hover:bg-indigo-100 font-medium transition-colors"
          >
            URL Bar (Go)
          </button>
          <button
            type="button"
            onClick={() => {
              onChangeEditorInputType('email');
              onChangeImeAction('IME_ACTION_NEXT');
              if (onChangeIsMultiLine) onChangeIsMultiLine(false);
            }}
            title="Form input: Enter key renders as 'Next' and advances to next field"
            className="px-2 py-0.5 rounded-md bg-sky-50 dark:bg-sky-950/40 text-sky-700 dark:text-sky-300 border border-sky-200 dark:border-sky-800/50 hover:bg-sky-100 font-medium transition-colors"
          >
            Form (Next)
          </button>
          <button
            type="button"
            onClick={() => {
              onChangeEditorInputType('text');
              onChangeImeAction('IME_ACTION_DONE');
              if (onChangeIsMultiLine) onChangeIsMultiLine(false);
            }}
            title="Single-line field: Enter key renders as checkmark 'Done' and commits"
            className="px-2 py-0.5 rounded-md bg-teal-50 dark:bg-teal-950/40 text-teal-700 dark:text-teal-300 border border-teal-200 dark:border-teal-800/50 hover:bg-teal-100 font-medium transition-colors"
          >
            Dialog (Done)
          </button>
        </div>

        {/* Action feedback flash */}
        {lastImeActionFeedback && (
          <div className="px-2.5 py-1 bg-blue-500/15 border border-blue-500/30 text-blue-600 dark:text-blue-300 rounded-lg text-xs font-medium animate-in fade-in flex items-center space-x-1.5">
            <Sparkles size={13} />
            <span>{lastImeActionFeedback}</span>
          </div>
        )}
      </div>

      {/* Simulated Chat Message (for testing AI smart replies & transliteration) */}
      <div className="flex items-start space-x-2.5 p-3 rounded-2xl bg-white/70 dark:bg-[#1E242B]/80 border border-neutral-200/80 dark:border-neutral-800 shadow-xs backdrop-blur-xs">
        <div className="w-8 h-8 rounded-full bg-blue-600/15 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0 font-bold text-xs">
          <MessageSquare size={16} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between text-[11px] text-neutral-400 mb-0.5">
            <span className="font-semibold text-neutral-600 dark:text-neutral-300">Incoming Message (Context)</span>
            <button
              type="button"
              onClick={onCycleIncomingMessage}
              title="Change incoming message to test different Smart Replies"
              className="flex items-center space-x-1 hover:text-blue-500 transition-colors"
            >
              <RefreshCw size={11} />
              <span>Change Prompt</span>
            </button>
          </div>
          <p className="text-sm font-medium text-neutral-800 dark:text-neutral-200">
            "{incomingMessage}"
          </p>
        </div>
      </div>

      {/* Main Text Input Box */}
      <div className="relative rounded-2xl bg-white dark:bg-[#181D23] border-2 border-blue-500/40 focus-within:border-blue-500 shadow-sm transition-all overflow-hidden flex flex-col">
        <div className="flex items-center justify-between px-3.5 py-2 border-b border-neutral-100 dark:border-neutral-800/80 bg-neutral-50/50 dark:bg-neutral-900/30 text-xs">
          <span className="font-semibold text-[11px] text-neutral-400 uppercase tracking-wider flex items-center gap-1.5 flex-wrap">
            <span>Active Input Field</span>
            {isStrictPrivacy && (
              <span className="text-[10px] text-amber-600 dark:text-amber-400 font-mono px-1.5 py-0.2 rounded bg-amber-500/10 border border-amber-500/20 font-bold">
                {isMaskedPassword ? 'TYPE_TEXT_VARIATION_PASSWORD (MASKED)' : 'TYPE_TEXT_VARIATION_VISIBLE_PASSWORD (UNMASKED)'}
              </span>
            )}
            {(editorInputType === 'phone' || editorInputType === 'number') && (
              <span className="text-[10px] text-blue-600 dark:text-blue-400 font-mono px-1.5 py-0.2 rounded bg-blue-500/10 border border-blue-500/20 font-bold">
                {editorInputType === 'phone' ? 'TYPE_CLASS_PHONE (NUMPAD)' : 'TYPE_CLASS_NUMBER (NUMPAD)'}
              </span>
            )}
          </span>
          <div className="flex items-center space-x-1.5">
            {/* Password view toggle for masked password */}
            {isMaskedPassword && (
              <button
                type="button"
                onClick={() => setShowPassword((p) => !p)}
                title={showPassword ? 'Hide password' : 'Show password'}
                className="p-1 rounded-md text-neutral-500 hover:text-neutral-900 dark:hover:text-white hover:bg-neutral-200/50 dark:hover:bg-neutral-800 flex items-center space-x-1 text-xs"
              >
                {showPassword ? <EyeOff size={13} /> : <Eye size={13} />}
                <span>{showPassword ? 'Hide' : 'Show'}</span>
              </button>
            )}

            {/* Undo button */}
            <button
              id="btn_area_undo"
              type="button"
              onClick={onUndo}
              disabled={historyCount === 0}
              title={historyCount > 0 ? `Undo last edit (${historyCount} saved)` : 'Undo (no history)'}
              className="p-1 rounded-md text-neutral-500 hover:text-neutral-900 dark:hover:text-white hover:bg-neutral-200/50 dark:hover:bg-neutral-800 disabled:opacity-30 flex items-center space-x-1 text-xs"
            >
              <RotateCcw size={13} />
              <span>Undo{historyCount > 0 ? ` (${historyCount})` : ''}</span>
            </button>

            {/* Clear History button */}
            <button
              id="btn_area_clear_history"
              type="button"
              onClick={onClearHistory}
              disabled={historyCount === 0}
              title={
                historyCount > 0
                  ? `Clear typing history (${historyCount} states) to prevent memory bloat`
                  : 'History buffer is empty'
              }
              className={`px-1.5 py-1 rounded-md text-xs flex items-center space-x-1 font-medium transition-colors ${
                historyCount > 0
                  ? 'text-red-500 hover:text-red-600 hover:bg-red-500/10 active:bg-red-500/20'
                  : 'text-neutral-400 opacity-25 cursor-not-allowed'
              }`}
            >
              <Trash2 size={13} />
              <span>Clear History</span>
            </button>

            <div className="h-3 w-px bg-neutral-200 dark:bg-neutral-700 mx-0.5" />

            {/* Direct Codebase ZIP Download */}
            <a
              id="btn_download_zip"
              href="/project_source.zip"
              download="babelkey_keyboard_project.zip"
              title="Download entire production codebase (React + Android IME + dictionaries) as a ZIP file"
              className="px-2 py-1 rounded-md bg-blue-600 hover:bg-blue-500 text-white font-medium flex items-center space-x-1 text-xs transition-colors shadow-xs"
            >
              <Download size={13} />
              <span>Download ZIP</span>
            </a>

            <button
              type="button"
              onClick={handleCopy}
              disabled={!text}
              title="Copy typed text"
              className="p-1 rounded-md text-neutral-500 hover:text-neutral-900 dark:hover:text-white hover:bg-neutral-200/50 dark:hover:bg-neutral-800 disabled:opacity-30 flex items-center space-x-1 text-xs"
            >
              {copied ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
            <button
              type="button"
              onClick={handleClear}
              disabled={!text}
              title="Clear text"
              className="p-1 rounded-md text-neutral-500 hover:text-red-500 hover:bg-neutral-200/50 dark:hover:bg-neutral-800 disabled:opacity-30"
            >
              <Trash2 size={13} />
            </button>
          </div>
        </div>

        <textarea
          id="active_text_input"
          value={displayedText}
          onFocus={onFocus}
          onChange={(e) => onTextChange(e.target.value, e.target.selectionStart || e.target.value.length)}
          onSelect={(e) => {
            const target = e.target as HTMLTextAreaElement;
            onTextChange(text, target.selectionStart || target.value.length);
          }}
          placeholder={
            isStrictPrivacy
              ? isMaskedPassword
                ? 'Type password here (masked, privacy protocol active)...'
                : 'Type password here (visible, privacy protocol active)...'
              : 'Tap keyboard keys below or type here...'
          }
          rows={3}
          className="w-full p-3.5 bg-transparent resize-none text-base focus:outline-hidden font-sans placeholder:text-neutral-400"
        />

        <div className="px-3 py-1.5 bg-neutral-50/60 dark:bg-neutral-900/40 border-t border-neutral-100 dark:border-neutral-800 text-[11px] text-neutral-400 flex items-center justify-between flex-wrap gap-1">
          <div className="flex items-center space-x-2 flex-wrap">
            <span>Cursor: {cursorPos}</span>
            <span>•</span>
            <span>{text.length} chars</span>
            <span>•</span>
            <span>{text.trim() ? text.trim().split(/\s+/).length : 0} words</span>
            <span>•</span>
            <span className="flex items-center space-x-1 font-mono">
              <span>History: {historyCount} states</span>
              {historyClearedNotice && (
                <span className="text-emerald-500 font-sans font-semibold animate-pulse">
                  (Memory freed!)
                </span>
              )}
            </span>
          </div>

          <div className="flex items-center space-x-2">
            {historyCount > 0 && (
              <button
                type="button"
                id="btn_footer_clear_history"
                onClick={onClearHistory}
                title="Reset typing history state immediately to prevent memory bloat"
                className="text-[10px] text-red-500 dark:text-red-400 hover:underline font-semibold flex items-center space-x-0.5"
              >
                <span>Reset History Buffer</span>
              </button>
            )}
            <span className="italic text-[10px] hidden sm:inline">Virtual or physical input</span>
          </div>
        </div>
      </div>

      {/* Feature test presets chips (including Surrogate & Emoji tests) */}
      <div className="flex items-center space-x-1.5 overflow-x-auto no-scrollbar text-xs py-0.5">
        <span className="text-[11px] font-semibold text-neutral-400 shrink-0 flex items-center gap-1">
          <Sparkles size={12} className="text-blue-500" />
          Test cases:
        </span>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('how are you')}
          className="px-2.5 py-1 rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/25 shrink-0 transition-colors font-medium"
          title="Tests English to Telugu translation (Tap translate icon on toolbar)"
        >
          Eng→Tel: <span className="font-mono">how are you</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('ela unnaru')}
          className="px-2.5 py-1 rounded-full bg-teal-500/15 text-teal-600 dark:text-teal-400 hover:bg-teal-500/25 shrink-0 transition-colors font-medium"
          title="Tests Telugu to English translation (Tap translate icon on toolbar)"
        >
          Tel→Eng: <span className="font-mono">ela unnaru</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('namaskaram')}
          className="px-2.5 py-1 rounded-full bg-teal-500/15 text-teal-600 dark:text-teal-400 hover:bg-teal-500/25 shrink-0 transition-colors font-medium"
          title="Tests Tel-Eng word translation (Tap translate icon on toolbar)"
        >
          Tel-Eng: <span className="font-mono">namaskaram</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('🔥👨‍👩‍👧‍👦🇮🇳👍🏽')}
          className="px-2.5 py-1 rounded-full bg-blue-500/15 text-blue-600 dark:text-blue-400 hover:bg-blue-500/25 shrink-0 transition-colors font-medium"
          title="Tests Gboard-style surrogate pair and compound emoji backspace deletion"
        >
          Test Emojis / UTF-16: <span className="font-mono">🔥👨‍👩‍👧‍👦🇮🇳👍🏽</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('nen')}
          className="px-2.5 py-1 rounded-full bg-blue-500/15 text-blue-600 dark:text-blue-400 hover:bg-blue-500/25 shrink-0 transition-colors font-medium"
          title="Tests Telugu-English suggestions (typing 'nen' suggests 'nenu', 'nene', etc. in Latin letters)"
        >
          Type Tel-Eng: <span className="font-mono">nen</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('hel')}
          className="px-2.5 py-1 rounded-full bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-500/25 shrink-0 transition-colors font-medium"
          title="Tests English suggestions (typing 'hel' suggests 'hello', 'help', etc.)"
        >
          Type English: <span className="font-mono">hel</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('unneru')}
          className="px-2.5 py-1 rounded-full bg-amber-500/15 text-amber-600 dark:text-amber-400 hover:bg-amber-500/25 shrink-0 transition-colors font-medium"
          title="Tests Autocorrect mistake correction (Tap Auto Correct ✨ button on toolbar)"
        >
          Autocorrect: <span className="font-mono">unneru</span> → <span className="font-mono">unnaru</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('nenu veltru')}
          className="px-2.5 py-1 rounded-full bg-neutral-200/70 dark:bg-neutral-800 hover:bg-blue-500/20 text-neutral-700 dark:text-neutral-300 shrink-0 transition-colors"
          title="Tests Telugu Grammar correction detection"
        >
          Try grammar: <span className="font-mono">nenu veltru</span>
        </button>
        <button
          type="button"
          onClick={() => onQuickSampleInsert('OTP: 839201')}
          className="px-2.5 py-1 rounded-full bg-neutral-200/70 dark:bg-neutral-800 hover:bg-blue-500/20 text-neutral-700 dark:text-neutral-300 shrink-0 transition-colors"
          title="Tests OTP recognition banner"
        >
          Detect OTP: <span className="font-mono">839201</span>
        </button>
      </div>
    </div>
  );
};

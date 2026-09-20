import React, { useState, useMemo } from 'react';
import { EMOJI_CATEGORIES } from '../data/emojiData';
import { ThemeId } from '../types';
import { Search, Delete, CornerDownLeft, X } from 'lucide-react';

interface EmojiKeyboardProps {
  themeId: ThemeId;
  onSelectEmoji: (emoji: string) => void;
  onBackspace: () => void;
  onClose: () => void;
}

export const EmojiKeyboard: React.FC<EmojiKeyboardProps> = ({
  themeId,
  onSelectEmoji,
  onBackspace,
  onClose,
}) => {
  const [selectedCategoryIdx, setSelectedCategoryIdx] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');

  const isLight = themeId === ThemeId.LIGHT;

  const currentCategory = EMOJI_CATEGORIES[selectedCategoryIdx];

  const filteredEmojis = useMemo(() => {
    if (!searchQuery.trim()) {
      return currentCategory.emojis;
    }
    const q = searchQuery.toLowerCase();
    // Search across all categories
    const all = EMOJI_CATEGORIES.flatMap((c) => c.emojis);
    // Unique list
    return Array.from(new Set(all));
  }, [searchQuery, currentCategory]);

  return (
    <div
      id="emoji_keyboard_view"
      className={`w-full h-full min-h-0 flex-1 flex flex-col select-none transition-colors ${
        isLight ? 'bg-neutral-100 text-neutral-900' : 'bg-[#1E2328] text-neutral-100'
      }`}
    >
      {/* Category Icons Bar */}
      <div className="flex items-center justify-between px-2 py-1.5 border-b border-neutral-500/20 overflow-x-auto no-scrollbar">
        <div className="flex items-center space-x-1">
          {EMOJI_CATEGORIES.map((cat, idx) => (
            <button
              key={cat.name}
              type="button"
              onClick={() => {
                setSelectedCategoryIdx(idx);
                setSearchQuery('');
              }}
              title={cat.name}
              className={`w-9 h-9 rounded-lg flex items-center justify-center text-lg transition-all cursor-pointer ${
                selectedCategoryIdx === idx && !searchQuery
                  ? isLight
                    ? 'bg-neutral-300 text-neutral-900 shadow-xs'
                    : 'bg-white/20 text-white shadow-xs'
                  : 'hover:bg-neutral-500/10 opacity-75'
              }`}
            >
              {cat.icon}
            </button>
          ))}
        </div>

        {/* Quick Clear or Back button */}
        <button
          id="btn_emoji_to_abc_top"
          type="button"
          onClick={onClose}
          className="p-1.5 rounded-lg hover:bg-neutral-500/20 text-xs font-semibold px-2 flex items-center space-x-1 cursor-pointer active:scale-95 transition-transform"
        >
          <span>ABC</span>
        </button>
      </div>

      {/* Emoji Search Input */}
      <div className="px-3 py-1.5 flex items-center border-b border-neutral-500/10">
        <div
          className={`w-full flex items-center px-2.5 py-1 rounded-lg text-xs space-x-2 ${
            isLight ? 'bg-white border border-neutral-300' : 'bg-neutral-800/80 border border-neutral-700'
          }`}
        >
          <Search size={14} className="opacity-50" />
          <input
            id="input_emoji_search"
            type="text"
            placeholder="Search all emojis..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-transparent focus:outline-hidden text-xs"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="opacity-60 hover:opacity-100"
            >
              <X size={13} />
            </button>
          )}
        </div>
      </div>

      {/* Emoji Grid */}
      <div className="flex-1 overflow-y-auto p-2">
        <div className="grid grid-cols-8 sm:grid-cols-10 gap-1.5">
          {filteredEmojis.map((emoji, index) => (
            <button
              key={`${emoji}-${index}`}
              type="button"
              onClick={() => onSelectEmoji(emoji)}
              className="h-10 rounded-lg flex items-center justify-center text-2xl hover:scale-125 active:scale-95 transition-transform duration-100 hover:bg-neutral-500/15 cursor-pointer"
            >
              {emoji}
            </button>
          ))}
        </div>
      </div>

      {/* Bottom Bar: Back to ABC, Space, Backspace */}
      <div className="h-11 px-3 border-t border-neutral-500/20 flex items-center justify-between">
        <button
          id="btn_emoji_to_abc"
          type="button"
          onClick={onClose}
          className={`h-9 px-4 rounded-lg flex items-center justify-center font-bold text-xs cursor-pointer active:scale-95 transition-transform ${
            isLight ? 'bg-neutral-300 text-neutral-800 hover:bg-neutral-400/70' : 'bg-neutral-800 text-neutral-200 hover:bg-neutral-700'
          }`}
        >
          ABC
        </button>

        <button
          type="button"
          onClick={() => onSelectEmoji(' ')}
          className={`h-9 flex-1 mx-3 rounded-lg text-xs flex items-center justify-center ${
            isLight ? 'bg-white border border-neutral-300 text-neutral-600' : 'bg-neutral-800 text-neutral-400'
          }`}
        >
          Space
        </button>

        <button
          type="button"
          onClick={onBackspace}
          className={`h-9 w-12 rounded-lg flex items-center justify-center ${
            isLight ? 'bg-neutral-300 text-neutral-800' : 'bg-neutral-800 text-neutral-200'
          }`}
        >
          <Delete size={18} />
        </button>
      </div>
    </div>
  );
};

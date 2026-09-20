import React, { useState } from 'react';
import { ThemeId } from '../types';
import { Plus, Trash2, X, MessageSquareQuote } from 'lucide-react';

interface PhrasesPanelProps {
  themeId: ThemeId;
  phrases: string[];
  onSelectPhrase: (phrase: string) => void;
  onAddPhrase: (phrase: string) => void;
  onDeletePhrase: (index: number) => void;
  onClose: () => void;
}

export const PhrasesPanel: React.FC<PhrasesPanelProps> = ({
  themeId,
  phrases,
  onSelectPhrase,
  onAddPhrase,
  onDeletePhrase,
  onClose,
}) => {
  const [newPhrase, setNewPhrase] = useState('');
  const isLight = themeId === ThemeId.LIGHT;

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPhrase.trim()) return;
    onAddPhrase(newPhrase.trim());
    setNewPhrase('');
  };

  return (
    <div
      id="phrases_panel_view"
      className={`w-full flex flex-col h-[280px] select-none transition-colors border-t ${
        isLight
          ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
          : 'bg-[#191F26] text-neutral-100 border-neutral-700'
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-neutral-500/20">
        <div className="flex items-center space-x-2">
          <MessageSquareQuote size={16} className="text-blue-500" />
          <span className="font-semibold text-xs uppercase tracking-wider text-blue-500">
            Quick Phrases
          </span>
          <span className="text-[11px] opacity-60">({phrases.length})</span>
        </div>

        <button
          type="button"
          onClick={onClose}
          className="p-1 rounded-md hover:bg-neutral-500/20 opacity-70 hover:opacity-100"
        >
          <X size={15} />
        </button>
      </div>

      {/* Add input */}
      <form onSubmit={handleAdd} className="px-3 py-1.5 flex items-center space-x-1.5 border-b border-neutral-500/10">
        <input
          id="input_new_phrase"
          type="text"
          placeholder="New quick phrase..."
          value={newPhrase}
          onChange={(e) => setNewPhrase(e.target.value)}
          className={`flex-1 px-2.5 py-1 text-xs rounded-md border focus:outline-hidden ${
            isLight ? 'bg-white border-neutral-300' : 'bg-neutral-800/80 border-neutral-700'
          }`}
        />
        <button
          type="submit"
          className="p-1.5 rounded-md bg-blue-600 hover:bg-blue-500 text-white cursor-pointer"
        >
          <Plus size={14} />
        </button>
      </form>

      {/* List */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
        {phrases.length === 0 ? (
          <div className="h-32 flex flex-col items-center justify-center text-xs opacity-50 space-y-1">
            <p>No quick phrases yet.</p>
            <p className="text-[11px]">Add standard replies or phrases above!</p>
          </div>
        ) : (
          phrases.map((phrase, idx) => (
            <div
              key={`${phrase}-${idx}`}
              className={`flex items-center justify-between p-2 rounded-lg border text-xs transition-colors ${
                isLight
                  ? 'bg-white border-neutral-200 hover:border-neutral-300'
                  : 'bg-neutral-800/60 border-neutral-700/60 hover:border-neutral-600'
              }`}
            >
              <span
                onClick={() => onSelectPhrase(phrase)}
                className="flex-1 cursor-pointer truncate pr-2 font-medium"
                title="Tap to insert phrase"
              >
                {phrase}
              </span>
              <button
                type="button"
                onClick={() => onDeletePhrase(idx)}
                className="p-1 text-red-400 hover:bg-red-500/10 rounded-md"
              >
                <Trash2 size={13} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

import React, { useState } from 'react';
import { ClipItem, ThemeId } from '../types';
import { Pin, Trash2, Plus, Copy, Check, X } from 'lucide-react';

interface ClipboardDrawerProps {
  themeId: ThemeId;
  clips: ClipItem[];
  onPasteClip: (text: string) => void;
  onTogglePin: (id: string) => void;
  onDeleteClip: (id: string) => void;
  onAddClip: (text: string) => void;
  onClose: () => void;
}

export const ClipboardDrawer: React.FC<ClipboardDrawerProps> = ({
  themeId,
  clips,
  onPasteClip,
  onTogglePin,
  onDeleteClip,
  onAddClip,
  onClose,
}) => {
  const [newClipText, setNewClipText] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const isLight = themeId === ThemeId.LIGHT;

  const pinnedClips = clips.filter((c) => c.pinned);
  const recentClips = clips.filter((c) => !c.pinned);

  const handleAddNew = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newClipText.trim()) return;
    onAddClip(newClipText.trim());
    setNewClipText('');
  };

  const handleReadSystemClipboard = async () => {
    try {
      if (navigator.clipboard && navigator.clipboard.readText) {
        const text = await navigator.clipboard.readText();
        if (text && text.trim()) {
          onAddClip(text.trim());
        }
      }
    } catch {
      // Clipboard read permission might not be granted in iframe
    }
  };

  return (
    <div
      id="clipboard_drawer_view"
      className={`w-full flex flex-col h-[280px] select-none transition-colors border-t ${
        isLight
          ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
          : 'bg-[#191F26] text-neutral-100 border-neutral-700'
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-neutral-500/20">
        <div className="flex items-center space-x-2">
          <span className="font-semibold text-xs uppercase tracking-wider text-blue-500">
            Clipboard Manager
          </span>
          <span className="text-[11px] opacity-60">({clips.length}/15 clips)</span>
        </div>

        <div className="flex items-center space-x-1.5">
          <button
            type="button"
            onClick={handleReadSystemClipboard}
            title="Import from system clipboard"
            className="px-2 py-1 rounded-md text-xs bg-blue-600 hover:bg-blue-500 text-white flex items-center space-x-1"
          >
            <Copy size={12} />
            <span>Sync</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md hover:bg-neutral-500/20 opacity-70 hover:opacity-100"
          >
            <X size={15} />
          </button>
        </div>
      </div>

      {/* Add custom clip inline form */}
      <form onSubmit={handleAddNew} className="px-3 py-1.5 flex items-center space-x-1.5 border-b border-neutral-500/10">
        <input
          id="input_new_clip"
          type="text"
          placeholder="Type new clipboard snippet..."
          value={newClipText}
          onChange={(e) => setNewClipText(e.target.value)}
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

      {/* Clips List */}
      <div className="flex-1 overflow-y-auto p-2 space-y-3">
        {clips.length === 0 ? (
          <div className="h-32 flex flex-col items-center justify-center text-xs opacity-50 space-y-1">
            <Copy size={24} />
            <p>No clipboard items saved yet.</p>
            <p className="text-[11px]">Copy text or add items above!</p>
          </div>
        ) : (
          <>
            {/* Pinned Section */}
            {pinnedClips.length > 0 && (
              <div>
                <div className="text-[10px] uppercase font-bold text-neutral-400 px-1 mb-1 flex items-center gap-1">
                  <Pin size={10} className="text-blue-400 fill-current" />
                  Pinned
                </div>
                <div className="grid grid-cols-1 gap-1.5">
                  {pinnedClips.map((clip) => (
                    <div
                      key={clip.id}
                      className={`flex items-center justify-between p-2 rounded-lg border transition-all text-xs ${
                        isLight
                          ? 'bg-white border-blue-300/60 hover:border-blue-400'
                          : 'bg-neutral-800/70 border-blue-500/40 hover:border-blue-400'
                      }`}
                    >
                      <span
                        onClick={() => onPasteClip(clip.text)}
                        className="flex-1 cursor-pointer truncate font-mono text-xs pr-2"
                        title="Click to paste into input"
                      >
                        {clip.text}
                      </span>
                      <div className="flex items-center space-x-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => onTogglePin(clip.id)}
                          title="Unpin clip"
                          className="p-1 rounded-md text-blue-500 hover:bg-blue-500/10"
                        >
                          <Pin size={13} className="fill-current" />
                        </button>
                        <button
                          type="button"
                          onClick={() => onDeleteClip(clip.id)}
                          title="Delete clip"
                          className="p-1 rounded-md text-red-400 hover:bg-red-500/10"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Recent Section */}
            {recentClips.length > 0 && (
              <div>
                <div className="text-[10px] uppercase font-bold text-neutral-400 px-1 mb-1">
                  Recent History
                </div>
                <div className="grid grid-cols-1 gap-1.5">
                  {recentClips.map((clip) => (
                    <div
                      key={clip.id}
                      className={`flex items-center justify-between p-2 rounded-lg border transition-all text-xs ${
                        isLight
                          ? 'bg-white border-neutral-200 hover:border-neutral-300'
                          : 'bg-neutral-800/50 border-neutral-700/60 hover:border-neutral-600'
                      }`}
                    >
                      <span
                        onClick={() => onPasteClip(clip.text)}
                        className="flex-1 cursor-pointer truncate font-mono text-xs pr-2"
                        title="Click to paste into input"
                      >
                        {clip.text}
                      </span>
                      <div className="flex items-center space-x-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => onTogglePin(clip.id)}
                          title="Pin clip"
                          className="p-1 rounded-md opacity-50 hover:opacity-100 hover:bg-neutral-500/15"
                        >
                          <Pin size={13} />
                        </button>
                        <button
                          type="button"
                          onClick={() => onDeleteClip(clip.id)}
                          title="Delete clip"
                          className="p-1 rounded-md text-red-400 hover:bg-red-500/10"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

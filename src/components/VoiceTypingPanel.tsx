import React, { useState, useEffect, useRef } from 'react';
import { ThemeId } from '../types';
import { Mic, MicOff, Check, X, AlertCircle } from 'lucide-react';

interface VoiceTypingPanelProps {
  themeId: ThemeId;
  onTranscriptReceived: (text: string) => void;
  onClose: () => void;
}

export const VoiceTypingPanel: React.FC<VoiceTypingPanelProps> = ({
  themeId,
  onTranscriptReceived,
  onClose,
}) => {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const recognitionRef = useRef<any>(null);

  const isLight = themeId === ThemeId.LIGHT;

  useEffect(() => {
    // Initialize Web Speech API
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setErrorMessage('Speech recognition is not supported in this browser environment.');
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.lang = 'en-US';

      recognition.onstart = () => {
        setIsListening(true);
        setErrorMessage(null);
      };

      recognition.onresult = (event: any) => {
        let interimTranscript = '';
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          if (event.results[i].isFinal) {
            setTranscript((prev) => (prev ? `${prev} ${event.results[i][0].transcript}` : event.results[i][0].transcript));
          } else {
            interimTranscript += event.results[i][0].transcript;
          }
        }
      };

      recognition.onerror = (event: any) => {
        console.warn('Speech recognition error:', event.error);
        if (event.error === 'not-allowed') {
          setErrorMessage('Microphone access was denied. Please allow microphone permissions.');
        } else {
          setErrorMessage(`Listening interrupted (${event.error})`);
        }
        setIsListening(false);
      };

      recognition.onend = () => {
        setIsListening(false);
      };

      recognitionRef.current = recognition;
      recognition.start();
    } catch (err) {
      setErrorMessage('Failed to start microphone service.');
    }

    return () => {
      if (recognitionRef.current) {
        recognitionRef.current.abort();
      }
    };
  }, []);

  const toggleListening = () => {
    if (!recognitionRef.current) return;
    if (isListening) {
      recognitionRef.current.stop();
    } else {
      setErrorMessage(null);
      try {
        recognitionRef.current.start();
      } catch {
        // Recognition already active
      }
    }
  };

  const handleInsert = () => {
    if (transcript.trim()) {
      onTranscriptReceived(transcript.trim());
    }
    onClose();
  };

  return (
    <div
      id="voice_typing_view"
      className={`w-full flex flex-col h-[280px] select-none transition-colors border-t items-center justify-between p-4 ${
        isLight
          ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
          : 'bg-[#191F26] text-neutral-100 border-neutral-700'
      }`}
    >
      {/* Top action row */}
      <div className="w-full flex items-center justify-between">
        <span className="text-xs font-bold uppercase tracking-wider text-blue-500">
          Voice Typing Assistant
        </span>
        <button
          type="button"
          onClick={onClose}
          className="p-1 rounded-md hover:bg-neutral-500/20 opacity-70 hover:opacity-100"
        >
          <X size={16} />
        </button>
      </div>

      {/* Main Mic Button & Pulse Wave */}
      <div className="flex flex-col items-center justify-center space-y-3 my-2">
        <div className="relative flex items-center justify-center">
          {isListening && (
            <div className="absolute w-24 h-24 rounded-full bg-blue-500/20 animate-ping" />
          )}
          <button
            type="button"
            onClick={toggleListening}
            className={`relative z-10 w-16 h-16 rounded-full flex items-center justify-center text-white transition-all shadow-lg cursor-pointer ${
              isListening
                ? 'bg-red-500 hover:bg-red-600 ring-4 ring-red-400/30 animate-pulse'
                : 'bg-blue-600 hover:bg-blue-500 ring-4 ring-blue-500/20'
            }`}
          >
            {isListening ? <Mic size={28} /> : <MicOff size={28} />}
          </button>
        </div>

        <div className="text-xs font-medium text-center">
          {isListening ? (
            <span className="text-blue-500 animate-pulse">Listening... Speak clearly now</span>
          ) : (
            <span className="opacity-60">Tap microphone to resume listening</span>
          )}
        </div>
      </div>

      {/* Transcript text preview or error message */}
      <div className="w-full max-w-md min-h-[44px] max-h-16 overflow-y-auto px-3 py-1.5 rounded-lg border text-xs text-center flex items-center justify-center bg-black/5 dark:bg-white/5 border-neutral-500/20">
        {errorMessage ? (
          <div className="flex items-center space-x-1 text-amber-500">
            <AlertCircle size={14} className="shrink-0" />
            <span>{errorMessage}</span>
          </div>
        ) : transcript ? (
          <span className="font-sans italic">"{transcript}"</span>
        ) : (
          <span className="opacity-40 italic">Spoken words will appear here...</span>
        )}
      </div>

      {/* Insert and Cancel buttons */}
      <div className="w-full flex items-center justify-center space-x-3 pt-2">
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-1.5 rounded-lg text-xs font-semibold hover:bg-neutral-500/20"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleInsert}
          disabled={!transcript.trim()}
          className="px-5 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-40 disabled:cursor-not-allowed flex items-center space-x-1.5"
        >
          <Check size={14} />
          <span>Insert Text</span>
        </button>
      </div>
    </div>
  );
};

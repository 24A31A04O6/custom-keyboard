import React, { useState, useEffect, useMemo } from 'react';
import { KeyboardStats, ThemeId } from '../types';
import {
  BarChart2,
  Zap,
  FileText,
  CheckCircle2,
  RotateCcw,
  X,
  Trash2,
  Cpu,
  Clock,
  Activity,
  Gauge,
  Timer,
} from 'lucide-react';

interface StatsPanelProps {
  themeId: ThemeId;
  stats: KeyboardStats;
  onResetStats: () => void;
  onClose: () => void;
  historyCount?: number;
  onClearHistory?: () => void;
}

export const StatsPanel: React.FC<StatsPanelProps> = ({
  themeId,
  stats,
  onResetStats,
  onClose,
  historyCount = 0,
  onClearHistory,
}) => {
  const isLight = themeId === ThemeId.LIGHT;

  // Real-time tick state updating every 500ms
  const [currentTime, setCurrentTime] = useState<number>(Date.now());
  const [speedMetricMode, setSpeedMetricMode] = useState<'realtime' | 'average'>('realtime');

  useEffect(() => {
    const timer = window.setInterval(() => {
      setCurrentTime(Date.now());
    }, 500);
    return () => window.clearInterval(timer);
  }, []);

  // Format seconds into MM:SS
  const formatTime = (totalSeconds: number) => {
    const safeSec = Math.max(0, Math.floor(totalSeconds));
    const mins = Math.floor(safeSec / 60);
    const secs = safeSec % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // 1. Session Duration calculations
  const sessionElapsedMs = Math.max(1000, currentTime - stats.sessionStartTime);
  const sessionElapsedSec = Math.floor(sessionElapsedMs / 1000);
  const sessionDurationMinutes = sessionElapsedMs / 60000;
  const activeDurationSec = Math.round(stats.activeTypingSeconds || 0);

  // 2. Typing Activity Detection
  const timeSinceLastActivity = stats.lastActiveTime ? currentTime - stats.lastActiveTime : Infinity;
  const isLiveTyping = timeSinceLastActivity < 3500;
  const isRecentlyActive = timeSinceLastActivity < 15000;

  // 3. Real-Time Rolling WPM (last 15 seconds window)
  const recentWindowMs = 15000;
  const recentEvents = useMemo(() => {
    return (stats.recentActivity || []).filter(
      (ev) => currentTime - ev.timestamp <= recentWindowMs
    );
  }, [stats.recentActivity, currentTime]);

  const recentChars = recentEvents.reduce((acc, ev) => acc + ev.chars, 0);
  const recentWords = recentEvents.reduce((acc, ev) => acc + ev.words, 0);
  // Standard typing calculation: 5 characters equal 1 word
  const recentStandardWords = Math.max(recentWords, recentChars / 5);

  const realTimeBurstWpm = useMemo(() => {
    if (!isLiveTyping && recentEvents.length === 0) return 0;
    const windowElapsedMs = Math.max(
      3000,
      Math.min(recentWindowMs, currentTime - (recentEvents[0]?.timestamp || currentTime) + 1000)
    );
    const windowMinutes = windowElapsedMs / 60000;
    return Math.min(220, Math.round(recentStandardWords / windowMinutes));
  }, [isLiveTyping, recentEvents, recentStandardWords, currentTime]);

  // 4. Session Average WPM
  const totalStandardWords = Math.max(stats.totalWords, Math.round(stats.totalCharacters / 5));
  const sessionAverageWpm = useMemo(() => {
    const mins = Math.max(0.08, sessionDurationMinutes);
    return Math.min(220, Math.round(totalStandardWords / mins));
  }, [totalStandardWords, sessionDurationMinutes]);

  // 5. Active Displayed WPM & Characters Per Minute (CPM)
  const displayWpm = speedMetricMode === 'realtime' ? realTimeBurstWpm : sessionAverageWpm;
  const charsPerMinute = Math.round(stats.totalCharacters / Math.max(0.08, sessionDurationMinutes));

  // 6. Typing Speed Category & Badging
  const getSpeedTier = (wpm: number) => {
    if (wpm >= 80) return { label: 'Turbo / Pro', color: 'text-purple-500 bg-purple-500/15 border-purple-500/30' };
    if (wpm >= 60) return { label: 'Fast', color: 'text-amber-500 bg-amber-500/15 border-amber-500/30' };
    if (wpm >= 40) return { label: 'Fluent', color: 'text-emerald-500 bg-emerald-500/15 border-emerald-500/30' };
    if (wpm >= 20) return { label: 'Casual', color: 'text-blue-500 bg-blue-500/15 border-blue-500/30' };
    return { label: 'Beginner', color: 'text-neutral-400 bg-neutral-500/15 border-neutral-500/30' };
  };

  const speedTier = getSpeedTier(displayWpm > 0 ? displayWpm : sessionAverageWpm);

  // 7. Accuracy calculation
  const totalCorrections = Object.values(stats.correctionCounts).reduce((a, b) => a + b, 0);
  const accuracyPercent =
    stats.totalWords > 0
      ? Math.max(60, Math.min(100, Math.round((stats.totalWords / (stats.totalWords + totalCorrections * 0.5)) * 100)))
      : 100;

  return (
    <div
      id="stats_panel_view"
      className={`w-full flex flex-col h-full min-h-[290px] max-h-[360px] select-none transition-colors border-t overflow-hidden ${
        isLight
          ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
          : 'bg-[#191F26] text-neutral-100 border-neutral-700'
      }`}
    >
      {/* Header with Live Indicator & Actions */}
      <div className="flex items-center justify-between px-3.5 py-2 border-b border-neutral-500/20 shrink-0">
        <div className="flex items-center space-x-2">
          <BarChart2 size={16} className="text-blue-500" />
          <span className="font-semibold text-xs uppercase tracking-wider text-blue-500">
            Real-Time WPM & Analytics
          </span>
          {/* Live Activity Pulse Indicator */}
          <div
            className={`flex items-center space-x-1 text-[10px] px-2 py-0.5 rounded-full font-medium border ${
              isLiveTyping
                ? 'bg-emerald-500/15 text-emerald-500 border-emerald-500/30'
                : isRecentlyActive
                ? 'bg-amber-500/15 text-amber-500 border-amber-500/30'
                : 'bg-neutral-500/15 text-neutral-400 border-neutral-500/20'
            }`}
          >
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                isLiveTyping
                  ? 'bg-emerald-500 animate-ping'
                  : isRecentlyActive
                  ? 'bg-amber-500'
                  : 'bg-neutral-400'
              }`}
            />
            <span>{isLiveTyping ? 'Live Typing' : isRecentlyActive ? 'Active' : 'Idle'}</span>
          </div>
        </div>

        <div className="flex items-center space-x-1.5">
          <button
            id="btn_reset_stats"
            type="button"
            onClick={onResetStats}
            title="Reset session statistics & start new timing"
            className="px-2 py-1 rounded-md text-[11px] font-medium text-neutral-400 hover:text-neutral-200 hover:bg-neutral-500/20 flex items-center space-x-1 transition-colors"
          >
            <RotateCcw size={12} />
            <span className="hidden sm:inline">Reset Session</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            title="Close statistics"
            className="p-1 rounded-md hover:bg-neutral-500/20 opacity-70 hover:opacity-100 transition-opacity"
          >
            <X size={15} />
          </button>
        </div>
      </div>

      {/* Scrollable Metrics Content */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2.5">
        {/* Real-time WPM Hero Card */}
        <div
          id="realtime_wpm_card"
          className={`p-3 rounded-2xl border transition-all ${
            isLight
              ? 'bg-white border-neutral-200 shadow-xs'
              : 'bg-neutral-800/70 border-neutral-700/70 shadow-sm'
          }`}
        >
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center space-x-1.5 text-xs font-semibold text-amber-500">
              <Zap size={14} className={isLiveTyping ? 'animate-bounce' : ''} />
              <span>Typing Velocity</span>
            </div>

            {/* Mode Switcher: Real-Time vs Session Average */}
            <div className="flex items-center bg-neutral-200/70 dark:bg-neutral-900/60 p-0.5 rounded-lg text-[10px] font-medium">
              <button
                type="button"
                id="btn_wpm_mode_realtime"
                onClick={() => setSpeedMetricMode('realtime')}
                className={`px-2 py-0.5 rounded-md transition-all ${
                  speedMetricMode === 'realtime'
                    ? 'bg-blue-600 text-white shadow-xs font-semibold'
                    : 'text-neutral-400 hover:text-neutral-200'
                }`}
              >
                Instant Pace
              </button>
              <button
                type="button"
                id="btn_wpm_mode_average"
                onClick={() => setSpeedMetricMode('average')}
                className={`px-2 py-0.5 rounded-md transition-all ${
                  speedMetricMode === 'average'
                    ? 'bg-blue-600 text-white shadow-xs font-semibold'
                    : 'text-neutral-400 hover:text-neutral-200'
                }`}
              >
                Session Avg
              </button>
            </div>
          </div>

          {/* Primary Speed & Tier Display */}
          <div className="flex items-baseline justify-between">
            <div className="flex items-baseline space-x-2">
              <span className="text-3xl sm:text-4xl font-extrabold font-mono tracking-tight text-neutral-900 dark:text-neutral-50">
                {displayWpm}
              </span>
              <span className="text-xs font-semibold text-neutral-400">WPM</span>
            </div>

            <div className="flex items-center space-x-2">
              <span
                className={`text-[11px] font-semibold px-2 py-0.5 rounded-full border ${speedTier.color}`}
              >
                {speedTier.label}
              </span>
            </div>
          </div>

          {/* Animated Speed Gauge Bar (0 to 100+ WPM) */}
          <div className="mt-2.5 mb-1.5">
            <div className="w-full bg-neutral-200/80 dark:bg-neutral-900/80 rounded-full h-2 overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-300 ease-out bg-gradient-to-r from-blue-500 via-emerald-500 to-amber-500"
                style={{
                  width: `${Math.min(100, Math.max(3, Math.round((displayWpm / 100) * 100)))}%`,
                }}
              />
            </div>
            <div className="flex justify-between items-center text-[9px] text-neutral-400 mt-1 font-mono">
              <span>0 WPM</span>
              <span>
                {speedMetricMode === 'realtime'
                  ? `Avg: ${sessionAverageWpm} WPM`
                  : `Instant: ${realTimeBurstWpm} WPM`}
              </span>
              <span>100+ WPM</span>
            </div>
          </div>

          {/* Real-Time Session Timing Strip */}
          <div className="mt-2.5 pt-2 border-t border-neutral-500/15 grid grid-cols-3 gap-2 text-center">
            <div className="flex flex-col items-center">
              <div className="flex items-center space-x-1 text-[10px] text-neutral-400 mb-0.5">
                <Clock size={11} className="text-blue-400" />
                <span>Session Elapsed</span>
              </div>
              <span className="text-xs font-bold font-mono text-neutral-700 dark:text-neutral-200">
                {formatTime(sessionElapsedSec)}
              </span>
            </div>

            <div className="flex flex-col items-center border-x border-neutral-500/15">
              <div className="flex items-center space-x-1 text-[10px] text-neutral-400 mb-0.5">
                <Timer size={11} className="text-emerald-400" />
                <span>Active Typing</span>
              </div>
              <span className="text-xs font-bold font-mono text-neutral-700 dark:text-neutral-200">
                {formatTime(activeDurationSec)}
              </span>
            </div>

            <div className="flex flex-col items-center">
              <div className="flex items-center space-x-1 text-[10px] text-neutral-400 mb-0.5">
                <Gauge size={11} className="text-purple-400" />
                <span>Speed CPM</span>
              </div>
              <span className="text-xs font-bold font-mono text-neutral-700 dark:text-neutral-200">
                {charsPerMinute}
              </span>
            </div>
          </div>
        </div>

        {/* Secondary Metrics: Volume, Accuracy, Corrections */}
        <div className="grid grid-cols-3 gap-2">
          {/* Total Volume */}
          <div
            className={`p-2.5 rounded-xl border flex flex-col items-center justify-center text-center ${
              isLight ? 'bg-white border-neutral-200' : 'bg-neutral-800/60 border-neutral-700/60'
            }`}
          >
            <div className="flex items-center space-x-1 text-blue-500 text-xs font-semibold mb-0.5">
              <FileText size={12} />
              <span>Volume</span>
            </div>
            <div className="text-lg font-bold font-mono">{stats.totalWords}</div>
            <div className="text-[10px] text-neutral-400 font-mono">{stats.totalCharacters} chars</div>
          </div>

          {/* Typing Accuracy */}
          <div
            className={`p-2.5 rounded-xl border flex flex-col items-center justify-center text-center ${
              isLight ? 'bg-white border-neutral-200' : 'bg-neutral-800/60 border-neutral-700/60'
            }`}
          >
            <div className="flex items-center space-x-1 text-emerald-500 text-xs font-semibold mb-0.5">
              <Activity size={12} />
              <span>Accuracy</span>
            </div>
            <div className="text-lg font-bold font-mono">{accuracyPercent}%</div>
            <div className="text-[10px] text-neutral-400 font-mono">
              {totalCorrections > 0 ? `${totalCorrections} fixes` : '100% clean'}
            </div>
          </div>

          {/* Corrections */}
          <div
            className={`p-2.5 rounded-xl border flex flex-col items-center justify-center text-center ${
              isLight ? 'bg-white border-neutral-200' : 'bg-neutral-800/60 border-neutral-700/60'
            }`}
          >
            <div className="flex items-center space-x-1 text-amber-500 text-xs font-semibold mb-0.5">
              <CheckCircle2 size={12} />
              <span>Auto-Fixes</span>
            </div>
            <div className="text-lg font-bold font-mono">{totalCorrections}</div>
            <div className="text-[10px] text-neutral-400 font-mono">Corrections</div>
          </div>
        </div>

        {/* Undo History Buffer Status */}
        <div
          className={`flex items-center justify-between p-2.5 rounded-xl border ${
            isLight ? 'bg-white border-neutral-200' : 'bg-neutral-800/60 border-neutral-700/60'
          }`}
        >
          <div className="flex items-center space-x-2">
            <Cpu size={15} className="text-purple-500" />
            <div>
              <div className="text-xs font-semibold">Undo History Buffer</div>
              <div className="text-[10px] text-neutral-400 font-mono">
                {historyCount > 0
                  ? `${historyCount} typing states retained in memory`
                  : '0 states (optimal memory footprint)'}
              </div>
            </div>
          </div>

          {historyCount > 0 && onClearHistory ? (
            <button
              type="button"
              id="btn_stats_clear_history"
              onClick={onClearHistory}
              title="Reset typing history buffer to prevent memory bloat"
              className="px-2 py-1 rounded-lg text-xs font-medium bg-red-500/15 text-red-500 hover:bg-red-500/25 flex items-center space-x-1 transition-colors cursor-pointer"
            >
              <Trash2 size={12} />
              <span>Clear History</span>
            </button>
          ) : (
            <span className="text-[10px] text-neutral-400 italic">Optimized</span>
          )}
        </div>

        {/* Frequently Corrected Typos */}
        <div>
          <div className="text-[11px] font-semibold text-neutral-400 uppercase tracking-wider mb-1.5 flex items-center justify-between">
            <span>Frequently Corrected Typos</span>
            <span className="text-[10px] lowercase font-normal">
              {Object.keys(stats.correctionCounts).length} tracked
            </span>
          </div>
          {Object.keys(stats.correctionCounts).length === 0 ? (
            <div className="text-xs opacity-50 py-2 text-center italic">
              No typos corrected yet. Keep typing to build accuracy data!
            </div>
          ) : (
            <div className="space-y-1">
              {Object.entries(stats.correctionCounts)
                .sort(([, a], [, b]) => b - a)
                .slice(0, 5)
                .map(([typo, count]) => (
                  <div
                    key={typo}
                    className={`flex items-center justify-between px-2.5 py-1.5 rounded-lg text-xs ${
                      isLight ? 'bg-neutral-200/60' : 'bg-neutral-800/50'
                    }`}
                  >
                    <span className="font-mono text-neutral-400">{typo}</span>
                    <span className="font-semibold text-emerald-400 font-mono">×{count}</span>
                  </div>
                ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

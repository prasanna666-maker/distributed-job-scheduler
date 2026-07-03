import { useState, useEffect, useCallback } from 'react';

/** Custom hook for polling-based data fetching with auto-refresh */
export function usePolling(fetchFn, intervalMs = 5000, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    try {
      const result = await fetchFn();
      setData(result);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, deps);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, intervalMs);
    return () => clearInterval(interval);
  }, [refresh, intervalMs]);

  return { data, loading, error, refresh };
}

/** Format timestamp to readable string */
export function formatTime(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return d.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/** Format duration in ms to readable */
export function formatDuration(ms) {
  if (!ms && ms !== 0) return '—';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${(ms / 60000).toFixed(1)}m`;
}

/** Status badge component */
export function StatusBadge({ status }) {
  const dot = { RUNNING: '⚡', COMPLETED: '✓', FAILED: '✕', QUEUED: '◦', CLAIMED: '⟳',
    SCHEDULED: '⏱', RETRYING: '↻', DEAD: '☠', ONLINE: '●', OFFLINE: '○', DRAINING: '◐' };
  return (
    <span className={`badge ${(status || '').toLowerCase()}`}>
      {dot[status] || '•'} {status}
    </span>
  );
}

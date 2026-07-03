import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { StatusBadge, formatTime, formatDuration } from '../hooks';

export default function JobExplorer() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [queues, setQueues] = useState([]);
  const [jobs, setJobs] = useState({ content: [], totalPages: 0, number: 0, totalElements: 0 });
  const [selectedJob, setSelectedJob] = useState(null);
  const [executions, setExecutions] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);

  const queueId = searchParams.get('queue') || '';
  const status = searchParams.get('status') || '';
  const page = parseInt(searchParams.get('page') || '0');

  useEffect(() => {
    api.getQueues(1).then(r => setQueues(r.content || [])).catch(() => {});
  }, []);

  useEffect(() => {
    if (!queueId) return;
    setLoading(true);
    api.getJobs(queueId, status, page)
      .then(r => { setJobs(r); setLoading(false); })
      .catch(() => setLoading(false));
  }, [queueId, status, page]);

  useEffect(() => {
    if (!queueId) return;
    const interval = setInterval(() => {
      api.getJobs(queueId, status, page).then(setJobs).catch(() => {});
    }, 5000);
    return () => clearInterval(interval);
  }, [queueId, status, page]);

  const loadJobDetails = async (job) => {
    setSelectedJob(job);
    try {
      const [exec, log] = await Promise.all([
        api.getExecutions(job.id),
        api.getJobLogs(job.id),
      ]);
      setExecutions(exec.content || []);
      setLogs(log.content || []);
    } catch (e) {
      console.error(e);
    }
  };

  const handleRequeue = async (jobId) => {
    try {
      await api.requeueJob(jobId);
      setSelectedJob(null);
      const r = await api.getJobs(queueId, status, page);
      setJobs(r);
    } catch (e) {
      alert(e.message);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Job Explorer</h1>
        <p className="page-subtitle">Browse, filter, and inspect jobs across queues</p>
      </div>

      <div className="filters-bar">
        <select className="form-select" value={queueId}
          onChange={e => setSearchParams({ queue: e.target.value, status, page: '0' })}>
          <option value="">Select Queue</option>
          {queues.map(q => <option key={q.id} value={q.id}>{q.name}</option>)}
        </select>
        <select className="form-select" value={status}
          onChange={e => setSearchParams({ queue: queueId, status: e.target.value, page: '0' })}>
          <option value="">All Statuses</option>
          {['QUEUED','SCHEDULED','CLAIMED','RUNNING','COMPLETED','FAILED','RETRYING','DEAD'].map(s =>
            <option key={s} value={s}>{s}</option>
          )}
        </select>
        <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>
          {jobs.totalElements || 0} jobs found
        </span>
      </div>

      {!queueId ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">🔍</div>
            <div className="empty-state-text">Select a queue to browse jobs</div>
          </div>
        </div>
      ) : loading ? (
        <div className="loading-center"><div className="spinner"></div></div>
      ) : (
        <div className="card">
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Attempts</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {(jobs.content || []).map(job => (
                  <tr key={job.id} style={{ cursor: 'pointer' }} onClick={() => loadJobDetails(job)}>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>#{job.id}</td>
                    <td><code style={{ color: 'var(--accent)', fontSize: 12 }}>{job.type}</code></td>
                    <td><StatusBadge status={job.status} /></td>
                    <td>{job.priority}</td>
                    <td>{job.attemptCount} / {job.maxRetries}</td>
                    <td>{formatTime(job.createdAt)}</td>
                    <td>
                      {job.status === 'DEAD' && (
                        <button className="btn btn-sm btn-secondary" onClick={(e) => { e.stopPropagation(); handleRequeue(job.id); }}>
                          ↻ Requeue
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {(!jobs.content || jobs.content.length === 0) && (
                  <tr>
                    <td colSpan={7} style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>
                      No jobs found
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {jobs.totalPages > 1 && (
            <div className="pagination">
              <button className="btn btn-secondary btn-sm" disabled={page === 0}
                onClick={() => setSearchParams({ queue: queueId, status, page: String(page - 1) })}>← Previous</button>
              <span>Page {page + 1} of {jobs.totalPages}</span>
              <button className="btn btn-secondary btn-sm" disabled={page >= jobs.totalPages - 1}
                onClick={() => setSearchParams({ queue: queueId, status, page: String(page + 1) })}>Next →</button>
            </div>
          )}
        </div>
      )}

      {selectedJob && (
        <div className="modal-overlay" onClick={() => setSelectedJob(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-title">Job #{selectedJob.id} — {selectedJob.type}</div>
            <div style={{ marginBottom: 16 }}>
              <StatusBadge status={selectedJob.status} />
              <span style={{ marginLeft: 12, color: 'var(--text-muted)', fontSize: 13 }}>
                {selectedJob.attemptCount} / {selectedJob.maxRetries} attempts
              </span>
            </div>
            <div className="card-title">Payload</div>
            <pre style={{ background: 'var(--bg-glass)', padding: 12, borderRadius: 8, fontSize: 12, overflow: 'auto', maxHeight: 120, color: 'var(--text-secondary)' }}>
              {JSON.stringify(selectedJob.payload, null, 2)}
            </pre>
            <div className="card-title" style={{ marginTop: 20 }}>Execution History</div>
            {executions.length === 0 ? (
              <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No executions yet</div>
            ) : (
              executions.map(ex => (
                <div key={ex.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--border)', fontSize: 12 }}>
                  <StatusBadge status={ex.status} />
                  <span style={{ marginLeft: 8, color: 'var(--text-muted)' }}>
                    Attempt {ex.attemptNumber} • {formatDuration(ex.durationMs)} • {formatTime(ex.startedAt)}
                  </span>
                  {ex.errorMessage && (
                    <div style={{ marginTop: 4, color: 'var(--error)', fontFamily: 'monospace' }}>
                      {ex.errorMessage}
                    </div>
                  )}
                </div>
              ))
            )}
            <div className="card-title" style={{ marginTop: 20 }}>Logs</div>
            <div style={{ maxHeight: 200, overflow: 'auto' }}>
              {logs.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No logs</div>
              ) : (
                logs.map(log => (
                  <div key={log.id} className={`log-entry ${log.level}`}>
                    <span className="log-time">{formatTime(log.createdAt)}</span>
                    <span className="log-level" style={{ color: log.level === 'ERROR' ? 'var(--error)' : log.level === 'WARN' ? 'var(--warning)' : 'var(--info)' }}>
                      {log.level}
                    </span>
                    {log.message}
                  </div>
                ))
              )}
            </div>
            <div style={{ marginTop: 20, display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              {selectedJob.status === 'DEAD' && (
                <button className="btn btn-primary" onClick={() => handleRequeue(selectedJob.id)}>↻ Requeue</button>
              )}
              <button className="btn btn-secondary" onClick={() => setSelectedJob(null)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

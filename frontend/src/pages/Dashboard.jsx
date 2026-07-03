import { useState, useEffect } from 'react';
import { api } from '../api';
import { usePolling, StatusBadge, formatTime } from '../hooks';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';

const COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#3b82f6'];

export default function Dashboard() {
  const [queueStats, setQueueStats] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [queues, setQueues] = useState([]);

  const { data: workers, loading: wLoading } = usePolling(() => api.getWorkers(), 5000, []);

  // For demo: fetch all queues from project 1
  useEffect(() => {
    async function load() {
      try {
        const qRes = await api.getQueues(1);
        const qList = qRes.content || [];
        setQueues(qList);
        const stats = await Promise.all(qList.map(q => api.getQueueStats(q.id)));
        setQueueStats(stats);
      } catch (e) { /* project may not exist yet */ }
    }
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  const totalJobs = queueStats.reduce((a, s) => a + (s.total || 0), 0);
  const totalRunning = queueStats.reduce((a, s) => a + (s.running || 0), 0);
  const totalCompleted = queueStats.reduce((a, s) => a + (s.completed || 0), 0);
  const totalFailed = queueStats.reduce((a, s) => a + (s.failed || 0), 0);
  const totalDead = queueStats.reduce((a, s) => a + (s.dead || 0), 0);
  const onlineWorkers = (workers || []).filter(w => w.status === 'ONLINE').length;

  const pieData = [
    { name: 'Queued', value: queueStats.reduce((a, s) => a + (s.queued || 0), 0) },
    { name: 'Running', value: totalRunning },
    { name: 'Completed', value: totalCompleted },
    { name: 'Failed', value: totalFailed },
    { name: 'Dead', value: totalDead },
  ].filter(d => d.value > 0);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Dashboard</h1>
        <p className="page-subtitle">
          <span className="pulse-dot" style={{ display: 'inline-block', marginRight: 8, verticalAlign: 'middle' }}></span>
          Live overview — auto-refreshing every 5s
        </p>
      </div>

      {/* KPI Cards */}
      <div className="stats-grid">
        <div className="stat-card accent">
          <div className="stat-value">{totalJobs}</div>
          <div className="stat-label">Total Jobs</div>
        </div>
        <div className="stat-card info">
          <div className="stat-value">{totalRunning}</div>
          <div className="stat-label">Running</div>
        </div>
        <div className="stat-card success">
          <div className="stat-value">{totalCompleted}</div>
          <div className="stat-label">Completed</div>
        </div>
        <div className="stat-card error">
          <div className="stat-value">{totalFailed + totalDead}</div>
          <div className="stat-label">Failed / Dead</div>
        </div>
        <div className="stat-card warning">
          <div className="stat-value">{onlineWorkers}</div>
          <div className="stat-label">Workers Online</div>
        </div>
        <div className="stat-card info">
          <div className="stat-value">{queues.length}</div>
          <div className="stat-label">Active Queues</div>
        </div>
      </div>

      <div className="grid-2">
        {/* Queue Health */}
        <div className="card">
          <div className="card-title">Queue Health</div>
          {queueStats.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">📊</div>
              <div className="empty-state-text">No queues found. Create a project and queue to get started.</div>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={queueStats}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="queueName" stroke="#64748b" fontSize={12} />
                <YAxis stroke="#64748b" fontSize={12} />
                <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, color: '#f1f5f9' }} />
                <Bar dataKey="queued" fill="#3b82f6" name="Queued" radius={[4, 4, 0, 0]} />
                <Bar dataKey="running" fill="#6366f1" name="Running" radius={[4, 4, 0, 0]} />
                <Bar dataKey="completed" fill="#10b981" name="Completed" radius={[4, 4, 0, 0]} />
                <Bar dataKey="failed" fill="#ef4444" name="Failed" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Job Distribution Pie */}
        <div className="card">
          <div className="card-title">Job Distribution</div>
          {pieData.length === 0 ? (
            <div className="empty-state"><div className="empty-state-text">No job data yet</div></div>
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={100} dataKey="value" label={({ name, value }) => `${name}: ${value}`}>
                  {pieData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, color: '#f1f5f9' }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Workers Table */}
      <div className="card" style={{ marginTop: 24 }}>
        <div className="card-title">Worker Status</div>
        {wLoading ? (
          <div className="loading-center"><div className="spinner"></div></div>
        ) : !workers || workers.length === 0 ? (
          <div className="empty-state"><div className="empty-state-text">No workers registered</div></div>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead><tr><th>Name</th><th>Hostname</th><th>Status</th><th>Load</th><th>Last Heartbeat</th></tr></thead>
              <tbody>
                {workers.map(w => (
                  <tr key={w.id}>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{w.workerName}</td>
                    <td>{w.hostname}</td>
                    <td><StatusBadge status={w.status} /></td>
                    <td>{w.currentLoad} / {w.concurrencyLimit}</td>
                    <td>{formatTime(w.lastHeartbeatAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

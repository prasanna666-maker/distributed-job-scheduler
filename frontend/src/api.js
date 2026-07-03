const API_BASE = 'http://localhost:8080/api';

function getToken() {
  return localStorage.getItem('token');
}

async function request(path, options = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (res.status === 401) {
    localStorage.removeItem('token');
    window.location.href = '/login';
    return;
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || 'Request failed');
  }
  return res.json();
}

export const api = {
  // Auth
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),

  // Organizations
  createOrg: (data) => request('/organizations', { method: 'POST', body: JSON.stringify(data) }),
  getOrg: (id) => request(`/organizations/${id}`),

  // Projects
  getProjects: (orgId, page = 0) => request(`/organizations/${orgId}/projects?page=${page}&size=20`),
  createProject: (orgId, data) => request(`/organizations/${orgId}/projects`, { method: 'POST', body: JSON.stringify(data) }),

  // Queues
  getQueues: (projectId, page = 0) => request(`/projects/${projectId}/queues?page=${page}&size=20`),
  createQueue: (projectId, data) => request(`/projects/${projectId}/queues`, { method: 'POST', body: JSON.stringify(data) }),
  updateQueue: (queueId, data) => request(`/queues/${queueId}`, { method: 'PATCH', body: JSON.stringify(data) }),
  pauseQueue: (queueId) => request(`/queues/${queueId}/pause`, { method: 'PATCH' }),
  resumeQueue: (queueId) => request(`/queues/${queueId}/resume`, { method: 'PATCH' }),
  getQueueStats: (queueId) => request(`/queues/${queueId}/stats`),

  // Jobs
  getJobs: (queueId, status = '', page = 0) => {
    let url = `/queues/${queueId}/jobs?page=${page}&size=20&sort=createdAt,desc`;
    if (status) url += `&status=${status}`;
    return request(url);
  },
  getJob: (jobId) => request(`/jobs/${jobId}`),
  createJob: (queueId, data) => request(`/queues/${queueId}/jobs`, { method: 'POST', body: JSON.stringify(data) }),
  requeueJob: (jobId) => request(`/jobs/${jobId}/requeue`, { method: 'POST' }),

  // Executions & Logs
  getExecutions: (jobId, page = 0) => request(`/jobs/${jobId}/executions?page=${page}&size=20&sort=startedAt,desc`),
  getJobLogs: (jobId, page = 0) => request(`/jobs/${jobId}/logs?page=${page}&size=50&sort=createdAt,desc`),

  // DLQ
  getDlq: (queueId, page = 0) => request(`/queues/${queueId}/dlq?page=${page}&size=20`),

  // Workers
  getWorkers: () => request('/workers'),

  // Retry Policies
  createRetryPolicy: (data) => request('/retry-policies', { method: 'POST', body: JSON.stringify(data) }),
};

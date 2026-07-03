import { BrowserRouter, Routes, Route, Link, NavLink, Navigate, useNavigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import JobExplorer from './pages/JobExplorer';
import Login from './pages/Login';
import Register from './pages/Register';

function Layout({ children }) {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem('user') || '{}');

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    navigate('/login');
  };

  return (
    <div className="app">
      <div className="sidebar">
        <div className="sidebar-logo">⚡ Scheduler</div>
        <div className="sidebar-subtitle">Distributed Engine</div>
        
        <div className="nav-section">
          <div className="nav-label">Core</div>
          <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            📊 Dashboard
          </NavLink>
          <NavLink to="/jobs" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            🔍 Job Explorer
          </NavLink>
        </div>

        <div className="nav-section" style={{ marginTop: 'auto' }}>
          <div className="nav-label">Account</div>
          <div style={{ padding: '0 12px 12px 12px', fontSize: '13px', color: 'var(--text-secondary)' }}>
            Signed in as:<br/>
            <strong style={{ color: 'var(--text-primary)' }}>{user.fullName || 'User'}</strong>
          </div>
          <button onClick={handleLogout} className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center' }}>
            🚪 Log Out
          </button>
        </div>
      </div>
      <div className="main-content">
        {children}
      </div>
    </div>
  );
}

function PrivateRoute({ children }) {
  const token = localStorage.getItem('token');
  return token ? <Layout>{children}</Layout> : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
        <Route path="/jobs" element={<PrivateRoute><JobExplorer /></PrivateRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

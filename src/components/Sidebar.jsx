import { NavLink, Link } from 'react-router-dom'
import {
  Bell, CalendarDays, Home, Map, MessageCircle, Newspaper, PackageSearch, ShieldCheck, Tag, Wrench,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Avatar } from './ui'

export const NAV = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/feed', label: 'Feed', icon: Newspaper },
  { to: '/map', label: 'Map', icon: Map },
  { to: '/marketplace', label: 'Buy & sell', icon: Tag },
  { to: '/services', label: 'Services', icon: Wrench },
  { to: '/lost-found', label: 'Lost & found', icon: PackageSearch },
  { to: '/events', label: 'Events', icon: CalendarDays },
  { to: '/messages', label: 'Messages', icon: MessageCircle, count: 'messages' },
  { to: '/alerts', label: 'Alerts', icon: Bell, count: 'alerts' },
]

export function Brand() {
  return (
    <Link to="/" className="brand">
      <div className="brand-mark">
        <span><Home size={16} strokeWidth={2.6} /></span>
      </div>
      <div className="brand-name">Neighborly</div>
    </Link>
  )
}

export default function Sidebar({ open, counts = {} }) {
  const { profile, isAdmin } = useAuth()
  return (
    <aside className={`sidebar ${open ? 'open' : ''}`}>
      <Brand />
      <nav className="nav">
        {NAV.map(({ to, label, icon: Icon, end, count }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
            <Icon size={24} strokeWidth={1.8} />
            <span>{label}</span>
            {count && counts[count] > 0 && <span className="nav-badge hot">{counts[count]}</span>}
          </NavLink>
        ))}
      </nav>

      {isAdmin && (
        <>
          <div className="nav-sep" />
          <NavLink to="/admin" className="nav-item">
            <ShieldCheck size={24} strokeWidth={1.8} />
            <span>Admin panel</span>
          </NavLink>
        </>
      )}

      <div className="sidebar-foot">
        {profile && (
          <Link to={`/profile/${profile.username}`} className="me-card">
            <Avatar src={profile.avatar_url} name={profile.full_name} size={40} />
            <div style={{ minWidth: 0 }}>
              <div className="name">{profile.full_name}</div>
              <div className="sub">{profile.neighborhood?.name || 'Set your neighborhood'}</div>
            </div>
          </Link>
        )}
      </div>
    </aside>
  )
}

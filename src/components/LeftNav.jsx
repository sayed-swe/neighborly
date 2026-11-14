// Left column on Home / Feed: profile, shortcuts, your neighborhood
import { Link, useLocation, useOutletContext } from 'react-router-dom'
import { MapPin, ShieldCheck } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Avatar } from './ui'
import { NAV } from './Sidebar'

export default function LeftNav() {
  const { profile, isAdmin } = useAuth()
  const { counts = {} } = useOutletContext() || {}
  const { pathname, search } = useLocation()
  const here = pathname + search

  return (
    <aside className="fb-left">
      <Link to={`/profile/${profile.username}`} className="shortcut">
        <Avatar src={profile.avatar_url} name={profile.full_name} size={36} />
        <span>{profile.full_name}</span>
      </Link>
      {NAV.filter((n) => n.to !== '/').map(({ to, label, icon: Icon, color, count }) => (
        <Link key={to} to={to} className={`shortcut ${here === to ? 'active' : ''}`}>
          <span className="shortcut-icon" style={{ background: color }}><Icon size={19} /></span>
          <span>{label}</span>
          {count && counts[count] > 0 && <span className="nav-badge hot">{counts[count]}</span>}
        </Link>
      ))}
      {isAdmin && (
        <Link to="/admin" className="shortcut">
          <span className="shortcut-icon" style={{ background: '#163222' }}><ShieldCheck size={19} /></span>
          <span>Admin panel</span>
        </Link>
      )}

      <div className="side-sep" />
      <div className="side-title">Your neighborhood</div>
      {profile.neighborhood ? (
        <Link to="/map" className="shortcut">
          <span className="shortcut-icon" style={{ background: 'var(--green-100)', color: 'var(--green-700)', borderRadius: 8 }}><MapPin size={19} /></span>
          <span>
            {profile.neighborhood.name}
            <div className="small muted" style={{ fontWeight: 400 }}>{[profile.neighborhood.area, profile.neighborhood.city].filter(Boolean).join(', ')}</div>
          </span>
        </Link>
      ) : (
        <Link to={`/profile/${profile.username}`} className="shortcut">
          <span className="shortcut-icon" style={{ background: 'var(--hover-dark)', color: 'var(--ink)', borderRadius: 8 }}><MapPin size={19} /></span>
          <span>Set your neighborhood</span>
        </Link>
      )}

      <p className="side-foot">Privacy · Terms · Community guidelines · Neighborly © {new Date().getFullYear()}</p>
    </aside>
  )
}

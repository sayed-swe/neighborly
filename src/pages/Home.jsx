import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, CalendarDays, HandHeart, MapPin, PackageSearch, Tag, Wrench } from 'lucide-react'
import { getAlerts, getEvents, getHelpRequests, getNeighbors } from '../lib/api'
import { formatDateTime, labelize, timeAgo } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { ComposerBox } from '../components/Composer'
import FeedList from '../components/FeedList'
import { Avatar } from '../components/ui'

export default function Home() {
  const { profile } = useAuth()
  const [reloadKey, setReloadKey] = useState(0)
  const [scope, setScope] = useState(profile.neighborhood_id ? 'mine' : 'all')

  const filters = scope === 'mine' && profile.neighborhood_id ? { neighborhoodId: profile.neighborhood_id } : {}

  return (
    <div className="page">
      <div className="with-rail">
        <div className="feed-col">
          <QuickLinks />
          <ComposerBox onPosted={() => setReloadKey((k) => k + 1)} />
          {profile.neighborhood_id && (
            <div className="chips">
              <button className={`chip ${scope === 'mine' ? 'active' : ''}`} onClick={() => setScope('mine')}>🏘️ {profile.neighborhood?.name}</button>
              <button className={`chip ${scope === 'all' ? 'active' : ''}`} onClick={() => setScope('all')}>🌐 All neighborhoods</button>
            </div>
          )}
          <FeedList filters={filters} reloadKey={reloadKey} />
        </div>
        <RightRail />
      </div>
    </div>
  )
}

function QuickLinks() {
  const items = [
    { to: '/marketplace', icon: Tag, label: 'Buy & sell', color: '#2f6db5', bg: 'var(--blue-soft)' },
    { to: '/services?tab=help', icon: HandHeart, label: 'Ask for help', color: '#e0663a', bg: 'var(--accent-soft)' },
    { to: '/lost-found', icon: PackageSearch, label: 'Lost & found', color: '#6b4fa0', bg: 'var(--purple-soft)' },
    { to: '/map', icon: MapPin, label: 'Nearby map', color: '#2f5d45', bg: 'var(--green-100)' },
  ]
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
      {items.map(({ to, icon: Icon, label, color, bg }) => (
        <Link key={to} to={to} className="card" style={{ padding: '12px 10px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, textAlign: 'center' }}>
          <div className="icon-tile" style={{ background: bg, color }}><Icon size={20} /></div>
          <span className="small bold">{label}</span>
        </Link>
      ))}
    </div>
  )
}

export function RightRail() {
  const { profile } = useAuth()
  const [alerts, setAlerts] = useState([])
  const [events, setEvents] = useState([])
  const [help, setHelp] = useState([])
  const [neighbors, setNeighbors] = useState([])

  useEffect(() => {
    getAlerts({ status: 'active' }).then((a) => setAlerts(a.slice(0, 3))).catch(() => {})
    getEvents({ when: 'upcoming' }).then((e) => setEvents(e.slice(0, 3))).catch(() => {})
    getHelpRequests({ status: 'active' }).then((h) => setHelp(h.slice(0, 3))).catch(() => {})
    getNeighbors(profile.neighborhood_id, profile.id, 6).then(setNeighbors).catch(() => {})
  }, [profile.neighborhood_id, profile.id])

  return (
    <aside className="rail">
      {alerts.length > 0 && (
        <div className="card card-pad">
          <div className="card-head"><div className="card-title"><AlertTriangle size={18} color="var(--red)" /> Emergency alerts</div><Link to="/alerts">See all</Link></div>
          <div className="list" style={{ gap: 8 }}>
            {alerts.map((a) => (
              <div key={a.id} className={`alert-card ${a.severity}`}>
                <h4>{a.title}</h4>
                <p className="clamp-2">{a.message}</p>
                <p className="small muted">{timeAgo(a.created_at)}{a.location_name ? ` · ${a.location_name}` : ''}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="card card-pad">
        <div className="card-head"><div className="card-title"><CalendarDays size={18} /> Upcoming events</div><Link to="/events">See all</Link></div>
        {events.length === 0 ? <p className="muted small">No upcoming events.</p> : (
          <div className="mini-list">
            {events.map((e) => {
              const d = new Date(e.starts_at)
              return (
                <Link key={e.id} to={`/events?item=${e.id}`} className="mini-item">
                  <div className="date-badge" style={{ width: 44 }}>
                    <div className="m" style={{ fontSize: 10 }}>{d.toLocaleString(undefined, { month: 'short' })}</div>
                    <div className="d" style={{ fontSize: 17, padding: '2px 0 3px' }}>{d.getDate()}</div>
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div className="t clamp-2">{e.title}</div>
                    <div className="s">{formatDateTime(e.starts_at)} · {e.event_participants?.length || 0} going</div>
                  </div>
                </Link>
              )
            })}
          </div>
        )}
      </div>

      {help.length > 0 && (
        <div className="card card-pad">
          <div className="card-head"><div className="card-title"><HandHeart size={18} color="var(--accent)" /> Neighbors need help</div><Link to="/services?tab=help">See all</Link></div>
          <div className="mini-list">
            {help.map((h) => (
              <Link key={h.id} to={`/services?tab=help&item=${h.id}`} className="mini-item">
                <Avatar src={h.author?.avatar_url} name={h.author?.full_name} size={36} />
                <div style={{ minWidth: 0 }}>
                  <div className="t clamp-2">{h.title}</div>
                  <div className="s">{labelize(h.help_type)} · {timeAgo(h.created_at)}</div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      <div className="card card-pad">
        <div className="card-head"><div className="card-title">👋 Neighbors</div><Link to="/services"><Wrench size={14} /> Find a pro</Link></div>
        {neighbors.length === 0 ? <p className="muted small">Invite your neighbors to join Neighborly.</p> : (
          <div className="mini-list">
            {neighbors.map((n) => (
              <Link key={n.id} to={`/profile/${n.username}`} className="mini-item">
                <Avatar src={n.avatar_url} name={n.full_name} size={36} />
                <div><div className="t">{n.full_name}</div><div className="s">@{n.username}</div></div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </aside>
  )
}

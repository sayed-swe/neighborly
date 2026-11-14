// Right column on Home / Feed: alerts, events, help requests, neighbors (contacts)
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertTriangle } from 'lucide-react'
import { getAlerts, getEvents, getHelpRequests, getNeighbors, startConversation } from '../lib/api'
import { formatDateTime, labelize, timeAgo } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Avatar } from './ui'

export default function RightRail() {
  const { profile } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [alerts, setAlerts] = useState([])
  const [events, setEvents] = useState([])
  const [help, setHelp] = useState([])
  const [neighbors, setNeighbors] = useState([])

  useEffect(() => {
    getAlerts({ status: 'active' }).then((a) => setAlerts(a.slice(0, 2))).catch(() => {})
    getEvents({ when: 'upcoming' }).then((e) => setEvents(e.slice(0, 2))).catch(() => {})
    getHelpRequests({ status: 'active' }).then((h) => setHelp(h.slice(0, 2))).catch(() => {})
    getNeighbors(profile.neighborhood_id, profile.id, 15)
      .then(async (list) => setNeighbors(list.length ? list : await getNeighbors(null, profile.id, 15)))
      .catch(() => {})
  }, [profile.neighborhood_id, profile.id])

  const chat = async (person) => {
    try { const id = await startConversation(person.id); navigate(`/messages/${id}`) } catch (e) { toast(e.message, 'error') }
  }

  return (
    <aside className="fb-right">
      {alerts.length > 0 && (
        <div className="rail-section">
          <div className="rail-title"><span className="flex items-center gap-6"><AlertTriangle size={17} color="var(--red)" /> Emergency alerts</span><Link to="/alerts">See all</Link></div>
          <div className="list" style={{ gap: 8, padding: '4px 8px' }}>
            {alerts.map((a) => (
              <Link key={a.id} to="/alerts" className={`alert-card ${a.severity}`}>
                <h4>{a.title}</h4>
                <p className="clamp-2">{a.message}</p>
                <p className="small muted">{timeAgo(a.created_at)}{a.location_name ? ` · ${a.location_name}` : ''}</p>
              </Link>
            ))}
          </div>
        </div>
      )}

      {events.length > 0 && (
        <div className="rail-section">
          <div className="rail-title"><span>Upcoming events</span><Link to="/events">See all</Link></div>
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

      {help.length > 0 && (
        <div className="rail-section">
          <div className="rail-title"><span>Neighbors need help</span><Link to="/services?tab=help">See all</Link></div>
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
      )}

      <div className="side-sep" />
      <div className="rail-section">
        <div className="rail-title"><span>Neighbors</span></div>
        {neighbors.length === 0 ? <p className="small muted" style={{ padding: 8 }}>Invite your neighbors to join Neighborly.</p> : neighbors.map((n) => (
          <button key={n.id} className="contact" onClick={() => chat(n)} title={`Message ${n.full_name}`}>
            <span className="avatar-wrap">
              <Avatar src={n.avatar_url} name={n.full_name} size={36} />
              {n.role !== 'resident' && <span className="role-dot" title={labelize(n.role)} />}
            </span>
            <span>{n.full_name}</span>
          </button>
        ))}
      </div>
    </aside>
  )
}

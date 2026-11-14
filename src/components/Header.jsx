import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { Bell, CheckCheck, LayoutGrid, Home, LogOut, MessageCircle, Search, ShieldCheck, SquarePen } from 'lucide-react'
import { getConversations, getNotifications, markAllNotificationsRead, markNotificationRead } from '../lib/api'
import { timeAgo } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { Avatar, Spinner } from './ui'
import { HEADER_TABS, NAV } from './Sidebar'

const NOTIF_ICON = { emergency: '🚨', reaction: '👍', comment: '💬', share: '🔁', event: '📅', help_request: '🤝', message: '✉️', system: '📣', report: '🛡️' }

export default function Header({ counts = {}, refreshCounts }) {
  const { profile, isAdmin, signOut } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [open, setOpen] = useState(null) // 'menu' | 'messages' | 'notifications' | 'account'
  const [q, setQ] = useState('')
  const ref = useRef(null)

  useEffect(() => setOpen(null), [pathname])
  useEffect(() => {
    if (!open) return
    const close = (e) => ref.current && !ref.current.contains(e.target) && setOpen(null)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const toggle = (name) => setOpen((o) => (o === name ? null : name))
  const tabs = HEADER_TABS.map((to) => NAV.find((n) => n.to === to))

  return (
    <header className="fb-header">
      <div className="fb-header-left">
        <Link to="/" className="fb-logo" aria-label="Neighborly home"><Home size={20} strokeWidth={2.6} /></Link>
        <form className="fb-search" onSubmit={(e) => { e.preventDefault(); navigate(`/feed?q=${encodeURIComponent(q.trim())}`) }}>
          <Search size={16} color="var(--muted)" />
          <input placeholder="Search Neighborly" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search" />
        </form>
      </div>

      <nav className="fb-tabs">
        {tabs.map(({ to, label, icon: Icon, end, count }) => (
          <NavLink key={to} to={to} end={end} title={label} aria-label={label} className={({ isActive }) => `fb-tab ${isActive ? 'active' : ''}`}>
            {({ isActive }) => (
              <>
                <Icon size={26} strokeWidth={isActive ? 2.3 : 1.9} />
                {count && counts[count] > 0 && <span className="dot-count">{counts[count]}</span>}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="fb-header-right" ref={ref}>
        <div className="dropdown">
          <button className={`round-btn ${open === 'menu' ? 'on' : ''}`} onClick={() => toggle('menu')} aria-label="Menu" title="Menu"><LayoutGrid size={20} /></button>
          {open === 'menu' && <MenuPanel isAdmin={isAdmin} />}
        </div>
        <div className="dropdown">
          <button className={`round-btn ${open === 'messages' ? 'on' : ''}`} onClick={() => toggle('messages')} aria-label="Messages" title="Messages">
            <MessageCircle size={20} />
            {counts.messages > 0 && <span className="dot-count">{counts.messages}</span>}
          </button>
          {open === 'messages' && <MessagesPanel me={profile.id} />}
        </div>
        <div className="dropdown">
          <button className={`round-btn ${open === 'notifications' ? 'on' : ''}`} onClick={() => toggle('notifications')} aria-label="Notifications" title="Notifications">
            <Bell size={20} />
            {counts.notifications > 0 && <span className="dot-count">{counts.notifications > 99 ? '99+' : counts.notifications}</span>}
          </button>
          {open === 'notifications' && <NotificationsPanel refreshCounts={refreshCounts} onNavigate={() => setOpen(null)} />}
        </div>
        <div className="dropdown">
          <button className="round-btn" onClick={() => toggle('account')} aria-label="Account" title="Account">
            <Avatar src={profile.avatar_url} name={profile.full_name} size={40} />
          </button>
          {open === 'account' && (
            <div className="panel" style={{ width: 340 }}>
              <div className="card" style={{ padding: 4, marginBottom: 8 }}>
                <Link to={`/profile/${profile.username}`} className="panel-row">
                  <Avatar src={profile.avatar_url} name={profile.full_name} size={40} />
                  <div><div className="t bold">{profile.full_name}</div><div className="s">See your profile</div></div>
                </Link>
              </div>
              {isAdmin && <Link to="/admin" className="panel-row"><span className="round-btn" style={{ width: 36, height: 36 }}><ShieldCheck size={18} /></span><span className="t">Admin panel</span></Link>}
              <button className="panel-row" onClick={signOut}><span className="round-btn" style={{ width: 36, height: 36 }}><LogOut size={18} /></span><span className="t">Log out</span></button>
              <p className="small muted" style={{ padding: '8px 8px 4px' }}>Neighborly © {new Date().getFullYear()}</p>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}

function MenuPanel({ isAdmin }) {
  return (
    <div className="panel">
      <div className="panel-head"><h3>Menu</h3></div>
      <div className="menu-grid">
        {NAV.map(({ to, label, icon: Icon, color }) => (
          <Link key={to} to={to} className="panel-row">
            <span className="shortcut-icon" style={{ background: color }}><Icon size={18} /></span>
            <span className="t" style={{ fontWeight: 500 }}>{label}</span>
          </Link>
        ))}
        {isAdmin && (
          <Link to="/admin" className="panel-row">
            <span className="shortcut-icon" style={{ background: '#163222' }}><ShieldCheck size={18} /></span>
            <span className="t" style={{ fontWeight: 500 }}>Admin panel</span>
          </Link>
        )}
      </div>
    </div>
  )
}

function MessagesPanel({ me }) {
  const [convos, setConvos] = useState(null)
  useEffect(() => { getConversations().then(setConvos).catch(() => setConvos([])) }, [])
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>Chats</h3>
        <Link to="/messages" className="icon-btn ghost" title="New message"><SquarePen size={18} /></Link>
      </div>
      {convos === null ? <Spinner /> : convos.length === 0 ? <p className="muted" style={{ padding: 8 }}>No messages yet.</p> : convos.slice(0, 8).map((c) => (
        <Link key={c.conversation_id} to={`/messages/${c.conversation_id}`} className={`panel-row ${Number(c.unread_count) > 0 ? 'unread' : ''}`}>
          <Avatar src={c.other_avatar} name={c.other_name} size={56} />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div className="t" style={{ fontWeight: Number(c.unread_count) > 0 ? 700 : 500 }}>{c.other_name}</div>
            <div className="s" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {c.last_sender_id === me ? 'You: ' : ''}{c.last_message || 'Say hello 👋'} · {timeAgo(c.last_message_at)}
            </div>
          </div>
          {Number(c.unread_count) > 0 && <span className="map-legend-dot" style={{ background: 'var(--green-700)', width: 12, height: 12 }} />}
        </Link>
      ))}
      <Link to="/messages" className="panel-foot">See all in Messages</Link>
    </div>
  )
}

function NotificationsPanel({ refreshCounts, onNavigate }) {
  const navigate = useNavigate()
  const [items, setItems] = useState(null)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const load = () => getNotifications().then(setItems).catch(() => setItems([]))
  useEffect(() => { load() }, [])

  const open = async (n) => {
    if (!n.is_read) { await markNotificationRead(n.id).catch(() => {}); refreshCounts?.() }
    onNavigate()
    if (n.link) navigate(n.link)
  }
  const readAll = async () => { await markAllNotificationsRead().catch(() => {}); load(); refreshCounts?.() }
  const shown = (items || []).filter((n) => !unreadOnly || !n.is_read)

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>Notifications</h3>
        <button className="icon-btn ghost" onClick={readAll} title="Mark all as read"><CheckCheck size={18} /></button>
      </div>
      <div className="chips" style={{ padding: '4px 8px 8px' }}>
        <button className={`chip ${!unreadOnly ? 'active' : ''}`} onClick={() => setUnreadOnly(false)}>All</button>
        <button className={`chip ${unreadOnly ? 'active' : ''}`} onClick={() => setUnreadOnly(true)}>Unread</button>
      </div>
      {items === null ? <Spinner /> : shown.length === 0 ? <p className="muted" style={{ padding: 8 }}>You're all caught up.</p> : shown.slice(0, 15).map((n) => (
        <button key={n.id} className={`panel-row ${n.is_read ? '' : 'unread'}`} onClick={() => open(n)}>
          <div style={{ position: 'relative' }}>
            <Avatar src={n.actor?.avatar_url} name={n.actor?.full_name || 'Neighborly'} size={56} />
            <span style={{ position: 'absolute', right: -4, bottom: -2, fontSize: 18 }}>{NOTIF_ICON[n.type] || '🔔'}</span>
          </div>
          <div style={{ flex: 1 }}>
            <div className="t" style={{ fontWeight: n.is_read ? 400 : 600 }}>{n.message}</div>
            <div className="s">{timeAgo(n.created_at)}</div>
          </div>
          {!n.is_read && <span className="map-legend-dot" style={{ background: 'var(--green-700)', width: 12, height: 12 }} />}
        </button>
      ))}
      <Link to="/alerts?tab=notifications" className="panel-foot">See all</Link>
    </div>
  )
}

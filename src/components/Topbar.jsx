import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Bell, LogOut, Menu, MessageCircle, Search, ShieldCheck, User } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Avatar, Dropdown } from './ui'
import { NAV } from './Sidebar'

export default function Topbar({ counts = {}, onMenu }) {
  const { profile, isAdmin, signOut } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [q, setQ] = useState('')

  const current = NAV.find((n) => (n.end ? pathname === n.to : pathname.startsWith(n.to)))
  const title = current?.label || (pathname.startsWith('/profile') ? 'Profile' : pathname.startsWith('/post') ? 'Post' : '')

  const submit = (e) => {
    e.preventDefault()
    navigate(`/feed?q=${encodeURIComponent(q.trim())}`)
  }

  return (
    <header className="topbar">
      <button className="icon-btn ghost menu-toggle" onClick={onMenu} aria-label="Open menu"><Menu size={22} /></button>
      <h1>{title}</h1>
      <form className="search" onSubmit={submit}>
        <Search size={18} color="var(--muted)" />
        <input placeholder="Search Neighborly" value={q} onChange={(e) => setQ(e.target.value)} />
      </form>
      <div className="topbar-actions">
        <Link to="/messages" className="icon-btn" aria-label="Messages">
          <MessageCircle size={20} />
          {counts.messages > 0 && <span className="dot-count">{counts.messages}</span>}
        </Link>
        <Link to="/alerts?tab=notifications" className="icon-btn" aria-label="Notifications">
          <Bell size={20} />
          {counts.notifications > 0 && <span className="dot-count">{counts.notifications > 99 ? '99+' : counts.notifications}</span>}
        </Link>
        {profile && (
          <Dropdown trigger={<button className="icon-btn ghost" style={{ padding: 0 }} aria-label="Account"><Avatar src={profile.avatar_url} name={profile.full_name} size={38} /></button>}>
            <Link to={`/profile/${profile.username}`}><User size={18} /> My profile</Link>
            {isAdmin && <Link to="/admin"><ShieldCheck size={18} /> Admin panel</Link>}
            <button onClick={signOut}><LogOut size={18} /> Log out</button>
          </Dropdown>
        )}
      </div>
    </header>
  )
}

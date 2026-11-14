// "Nearby now" — story-style cards for what's happening around you:
// active alerts, upcoming events, new listings and lost & found reports.
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, CalendarDays, PackageSearch, Plus, Tag } from 'lucide-react'
import { getAlerts, getEvents, getListings, getLostFound } from '../lib/api'
import { formatPrice } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { Avatar } from './ui'

const KINDS = {
  alert: { label: 'Alert', icon: AlertTriangle, bg: 'linear-gradient(160deg, #f0624d, #a3201a)' },
  event: { label: 'Event', icon: CalendarDays, bg: 'linear-gradient(160deg, #f5a623, #c25e00)' },
  listing: { label: 'For sale', icon: Tag, bg: 'linear-gradient(160deg, #4b9bff, #1453b8)' },
  lost_found: { label: 'Lost & found', icon: PackageSearch, bg: 'linear-gradient(160deg, #a07be8, #5a2fb0)' },
}

export default function NearbyStrip({ onCreate }) {
  const { profile } = useAuth()
  const navigate = useNavigate()
  const [cards, setCards] = useState([])

  useEffect(() => {
    Promise.all([
      getAlerts({ status: 'active' }).catch(() => []),
      getEvents({ when: 'upcoming' }).catch(() => []),
      getListings({}).catch(() => []),
      getLostFound({}).catch(() => []),
    ]).then(([alerts, events, listings, lost]) => {
      setCards([
        ...alerts.slice(0, 2).map((a) => ({ kind: 'alert', id: a.id, title: a.title, person: a.author, link: '/alerts' })),
        ...events.slice(0, 2).map((e) => ({ kind: 'event', id: e.id, title: e.title, image: e.image_url, person: e.creator, link: `/events?item=${e.id}` })),
        ...listings.slice(0, 3).map((l) => ({ kind: 'listing', id: l.id, title: `${formatPrice(l.price, l.listing_type)} · ${l.title}`, image: l.image_url, person: l.author, link: `/marketplace?item=${l.id}` })),
        ...lost.slice(0, 2).map((l) => ({ kind: 'lost_found', id: l.id, title: `${l.report_type === 'lost' ? 'Lost' : 'Found'}: ${l.item_name}`, image: l.image_url, person: l.author, link: `/lost-found?item=${l.id}` })),
      ])
    })
  }, [])

  return (
    <div className="nearby-strip">
      <div className="nearby-card create-card" onClick={onCreate} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onCreate()}>
        <div className="top">
          {profile.avatar_url ? <img src={profile.avatar_url} alt="" /> : <Avatar name={profile.full_name} size={140} />}
        </div>
        <span className="plus"><Plus size={22} strokeWidth={3} /></span>
        <div className="label">Create post</div>
      </div>
      {cards.map((c) => {
        const k = KINDS[c.kind]
        return (
          <div key={`${c.kind}-${c.id}`} className="nearby-card" style={{ background: k.bg }} onClick={() => navigate(c.link)}
            role="link" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && navigate(c.link)}>
            {c.image && <img className="bg" src={c.image} alt="" loading="lazy" />}
            <span className="ring">{c.person ? <Avatar src={c.person.avatar_url} name={c.person.full_name} size={32} /> : <k.icon size={18} />}</span>
            <div className="kind">{k.label}</div>
            <div className="title">{c.title}</div>
          </div>
        )
      })}
    </div>
  )
}

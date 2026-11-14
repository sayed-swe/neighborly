import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Calendar, Camera, Flag, Home, MapPin, MessageCircle, Pencil, Phone, Tag, UserX } from 'lucide-react'
import {
  getListings, getNeighborhoods, getProfileByUsername, startConversation, updateProfile, uploadImage,
} from '../lib/api'
import { formatDate, formatPrice, labelize } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import FeedList from '../components/FeedList'
import { ComposerBox } from '../components/Composer'
import ReportModal from '../components/ReportModal'
import { Avatar, Empty, ErrorText, Field, Modal, Spinner } from '../components/ui'

export default function Profile() {
  const { username } = useParams()
  const { profile: me, refreshProfile } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [person, setPerson] = useState(undefined)
  const [tab, setTab] = useState('posts')
  const [modal, setModal] = useState(null)
  const [listings, setListings] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  const load = () => getProfileByUsername(username).then(setPerson).catch(() => setPerson(null))
  useEffect(() => { setPerson(undefined); setTab('posts'); load() }, [username]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (tab === 'listings' && person) getListings({ userId: person.id, status: null }).then(setListings).catch(() => setListings([]))
  }, [tab, person])

  if (person === undefined) return <Spinner />
  if (person === null) return <div className="page"><div className="card"><Empty icon={UserX} title="User not found" /></div></div>

  const isMe = person.id === me.id

  const message = async () => {
    try { const id = await startConversation(person.id); navigate(`/messages/${id}`) } catch (e) { toast(e.message, 'error') }
  }

  return (
    <div>
      <div className="profile-top">
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <div className="profile-cover">{person.cover_url && <img src={person.cover_url} alt="" />}</div>
          <div className="profile-head">
            <Avatar src={person.avatar_url} name={person.full_name} size={168} />
            <div className="grow">
              <h2>{person.full_name}</h2>
              <div className="muted bold">@{person.username}{person.neighborhood ? ` · ${person.neighborhood.name}` : ''}
                {person.role !== 'resident' && <span className={`pill ${person.role === 'admin' ? 'green' : 'blue'}`} style={{ marginLeft: 8 }}>{labelize(person.role)}</span>}
              </div>
            </div>
            <div className="profile-actions">
              {isMe ? (
                <>
                  <button className="btn btn-primary" onClick={() => setModal('edit')}><Pencil size={17} /> Edit profile</button>
                </>
              ) : (
                <>
                  <button className="btn btn-primary" onClick={message}><MessageCircle size={17} /> Message</button>
                  <button className="btn btn-secondary" onClick={() => setModal('report')} aria-label="Report"><Flag size={17} /></button>
                </>
              )}
            </div>
          </div>
          <div className="profile-tabs">
            <button className={`tab ${tab === 'posts' ? 'active' : ''}`} onClick={() => setTab('posts')}>Posts</button>
            <button className={`tab ${tab === 'about' ? 'active' : ''}`} onClick={() => setTab('about')}>About</button>
            <button className={`tab ${tab === 'listings' ? 'active' : ''}`} onClick={() => setTab('listings')}>Listings</button>
          </div>
        </div>
      </div>

      <div className="page" style={{ paddingTop: 16, maxWidth: 1000 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 340px) minmax(0, 1fr)', gap: 20, alignItems: 'start' }} className="profile-grid">
          <div className="card card-pad">
            <div className="card-title mb-12">Intro</div>
            {person.bio && <p style={{ textAlign: 'center', marginBottom: 12 }}>{person.bio}</p>}
            <div className="list" style={{ gap: 10 }}>
              {person.neighborhood && <div className="flex gap-8 items-center"><Home size={18} className="muted" /> Lives in <b>{person.neighborhood.name}</b></div>}
              {person.address && <div className="flex gap-8 items-center"><MapPin size={18} className="muted" /> {person.address}</div>}
              {person.phone && <div className="flex gap-8 items-center"><Phone size={18} className="muted" /> {person.phone}</div>}
              <div className="flex gap-8 items-center"><Calendar size={18} className="muted" /> Joined {formatDate(person.created_at, { day: undefined })}</div>
            </div>
            {isMe && <button className="btn btn-secondary btn-block mt-16" onClick={() => setModal('edit')}>Edit details</button>}
          </div>

          <div className="flex" style={{ flexDirection: 'column', gap: 16 }}>
            {tab === 'posts' && (
              <>
                {isMe && <ComposerBox onPosted={() => setReloadKey((k) => k + 1)} />}
                <FeedList filters={{ userId: person.id }} reloadKey={reloadKey} emptyText={isMe ? 'Share your first post with the neighborhood.' : 'No posts yet.'} />
              </>
            )}
            {tab === 'listings' && (
              listings === null ? <Spinner /> : listings.length === 0 ? <div className="card"><Empty icon={Tag} title="No listings" /></div> : (
                <div className="grid-cards">
                  {listings.map((l) => (
                    <Link key={l.id} to={`/marketplace?item=${l.id}`} className="card item-card">
                      <div className="item-thumb">{l.image_url ? <img src={l.image_url} alt="" /> : <Tag size={30} />}</div>
                      <div className="item-info"><div className="item-price">{formatPrice(l.price, l.listing_type)}</div><div className="item-title">{l.title}</div><div className="item-sub">{labelize(l.status)}</div></div>
                    </Link>
                  ))}
                </div>
              )
            )}
            {tab === 'about' && (
              <div className="card card-pad">
                <div className="list" style={{ gap: 12 }}>
                  <div><div className="small muted">Full name</div><div className="bold">{person.full_name}</div></div>
                  <div><div className="small muted">Neighborhood</div><div className="bold">{person.neighborhood?.name || '—'}</div></div>
                  <div><div className="small muted">Bio</div><div>{person.bio || '—'}</div></div>
                  {person.gender && <div><div className="small muted">Gender</div><div>{labelize(person.gender)}</div></div>}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {modal === 'edit' && <EditProfileModal person={person} onClose={() => setModal(null)} onSaved={(p) => { setModal(null); refreshProfile(); if (p.username !== username) navigate(`/profile/${p.username}`, { replace: true }); else load() }} />}
      {modal === 'report' && <ReportModal targetType="user" targetId={person.id} onClose={() => setModal(null)} />}
      <style>{`@media (max-width: 900px) { .profile-grid { grid-template-columns: 1fr !important; } }`}</style>
    </div>
  )
}

function EditProfileModal({ person, onClose, onSaved }) {
  const [form, setForm] = useState({
    full_name: person.full_name || '', username: person.username || '', bio: person.bio || '', phone: person.phone || '',
    address: person.address || '', neighborhood_id: person.neighborhood_id || '', gender: person.gender || '', date_of_birth: person.date_of_birth || '',
  })
  const [avatar, setAvatar] = useState(null)
  const [cover, setCover] = useState(null)
  const [neighborhoods, setNeighborhoods] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { getNeighborhoods().then(setNeighborhoods).catch(() => {}) }, [])
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const save = async () => {
    setBusy(true); setError('')
    try {
      const values = {
        full_name: form.full_name.trim(), username: form.username.trim().toLowerCase(), bio: form.bio.trim() || null,
        phone: form.phone.trim() || null, address: form.address.trim() || null,
        neighborhood_id: form.neighborhood_id ? Number(form.neighborhood_id) : null,
        gender: form.gender || null, date_of_birth: form.date_of_birth || null,
      }
      if (avatar) values.avatar_url = await uploadImage(avatar, 'avatar')
      if (cover) values.cover_url = await uploadImage(cover, 'cover')
      const p = await updateProfile(person.id, values)
      onSaved(p)
    } catch (e) {
      setError(e.message.includes('profiles_username_key') ? 'That username is taken.' : e.message)
    } finally { setBusy(false) }
  }

  return (
    <Modal title="Edit profile" onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save'}</button></>}>
      <ErrorText error={error} />
      <div className="row">
        <Field label="Profile photo">
          <label className="btn btn-outline"><Camera size={17} /> {avatar ? avatar.name.slice(0, 18) : 'Choose photo'}
            <input type="file" accept="image/*" hidden onChange={(e) => setAvatar(e.target.files?.[0] || null)} /></label>
        </Field>
        <Field label="Cover photo">
          <label className="btn btn-outline"><Camera size={17} /> {cover ? cover.name.slice(0, 18) : 'Choose cover'}
            <input type="file" accept="image/*" hidden onChange={(e) => setCover(e.target.files?.[0] || null)} /></label>
        </Field>
      </div>
      <div className="row">
        <Field label="Full name"><input className="input" value={form.full_name} onChange={set('full_name')} maxLength={100} /></Field>
        <Field label="Username"><input className="input" value={form.username} onChange={set('username')} maxLength={30} /></Field>
      </div>
      <Field label="Bio"><textarea className="textarea" style={{ minHeight: 70 }} value={form.bio} onChange={set('bio')} maxLength={500} /></Field>
      <Field label="Neighborhood">
        <select className="select" value={form.neighborhood_id} onChange={set('neighborhood_id')}>
          <option value="">Not set</option>
          {neighborhoods.map((n) => <option key={n.id} value={n.id}>{n.name}{n.city ? `, ${n.city}` : ''}</option>)}
        </select>
      </Field>
      <div className="row">
        <Field label="Phone"><input className="input" value={form.phone} onChange={set('phone')} maxLength={20} /></Field>
        <Field label="Address"><input className="input" value={form.address} onChange={set('address')} maxLength={255} /></Field>
      </div>
      <div className="row">
        <Field label="Gender">
          <select className="select" value={form.gender} onChange={set('gender')}>
            <option value="">Prefer not to say</option><option value="male">Male</option><option value="female">Female</option><option value="other">Other</option>
          </select>
        </Field>
        <Field label="Date of birth"><input className="input" type="date" value={form.date_of_birth} onChange={set('date_of_birth')} /></Field>
      </div>
    </Modal>
  )
}

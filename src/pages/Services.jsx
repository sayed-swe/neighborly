import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  BadgeCheck, CheckCircle2, HandHeart, MapPin, MessageCircle, Phone, Plus, Search, Trash2, Wrench,
} from 'lucide-react'
import {
  createHelpRequest, createService, deleteHelpRequest, deleteService, getHelpRequests, getServiceCategories,
  getServices, respondToHelp, startConversation, updateHelpRequest, uploadImage,
} from '../lib/api'
import { HELP_TYPES } from '../lib/constants'
import { labelize, timeAgo } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Avatar, Empty, ErrorText, Field, Modal, Spinner, StatusPill } from '../components/ui'
import { ImagePicker, LocationField } from '../components/FormBits'
import ReportModal from '../components/ReportModal'

export default function Services() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'help' ? 'help' : 'services'
  return (
    <div className="page">
      <div className="page-head">
        <div><h2>Services & help</h2><p>Trusted local pros, and neighbors helping neighbors.</p></div>
      </div>
      <div className="tabs">
        <button className={`tab ${tab === 'services' ? 'active' : ''}`} onClick={() => setParams({})}><Wrench size={16} /> Local services</button>
        <button className={`tab ${tab === 'help' ? 'active' : ''}`} onClick={() => setParams({ tab: 'help' })}><HandHeart size={16} /> Help requests</button>
      </div>
      {tab === 'services' ? <ServiceDirectory highlight={params.get('item')} /> : <HelpBoard highlight={params.get('item')} />}
    </div>
  )
}

/* ------------------------- Service directory ------------------------- */
function ServiceDirectory({ highlight }) {
  const { profile, isAdmin } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [categories, setCategories] = useState([])
  const [categoryId, setCategoryId] = useState('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [items, setItems] = useState(null)
  const [creating, setCreating] = useState(false)
  const [report, setReport] = useState(null)

  useEffect(() => { getServiceCategories().then(setCategories).catch(() => {}) }, [])
  const load = () => getServices({ categoryId: categoryId || undefined, search: q || undefined }).then(setItems).catch(() => setItems([]))
  useEffect(() => { setItems(null); load() }, [categoryId, q]) // eslint-disable-line react-hooks/exhaustive-deps

  const message = async (userId) => {
    try { const id = await startConversation(userId); navigate(`/messages/${id}`) } catch (e) { toast(e.message, 'error') }
  }
  const remove = async (s) => {
    if (!confirm('Remove this service?')) return
    try { await deleteService(s.id); load() } catch (e) { toast(e.message, 'error') }
  }

  return (
    <>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setQ(search.trim()) }}>
        <div className="search"><Search size={18} color="var(--muted)" /><input placeholder="Electrician, tutor, plumber…" value={search} onChange={(e) => setSearch(e.target.value)} /></div>
        <button type="button" className="btn btn-primary" style={{ marginLeft: 'auto' }} onClick={() => setCreating(true)}><Plus size={18} /> List your service</button>
      </form>
      <div className="chips mb-16">
        <button className={`chip ${!categoryId ? 'active' : ''}`} onClick={() => setCategoryId('')}>All</button>
        {categories.map((c) => <button key={c.id} className={`chip ${String(categoryId) === String(c.id) ? 'active' : ''}`} onClick={() => setCategoryId(c.id)}>{c.name}</button>)}
      </div>

      {items === null ? <Spinner /> : items.length === 0 ? (
        <div className="card"><Empty icon={Wrench} title="No services listed yet" text="Are you a plumber, tutor or mechanic? List your service for neighbors." /></div>
      ) : (
        <div className="grid-cards" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))' }}>
          {items.map((s) => (
            <div key={s.id} className="card card-pad" style={String(s.id) === highlight ? { borderColor: 'var(--green-600)', boxShadow: '0 0 0 3px rgba(61,116,87,.15)' } : undefined}>
              <div className="flex gap-12">
                {s.image_url ? <img src={s.image_url} alt="" className="thumb-sq" style={{ width: 56, height: 56 }} /> : <div className="icon-tile" style={{ width: 56, height: 56 }}><Wrench size={24} /></div>}
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div className="flex gap-6 items-center wrap">
                    <span className="pill">{s.category?.name}</span>
                    {s.is_verified && <span className="pill green"><BadgeCheck size={12} /> Verified</span>}
                  </div>
                  <h4 className="mt-8">{s.service_name}</h4>
                  <Link to={`/profile/${s.provider?.username}`} className="small muted">by {s.provider?.full_name}</Link>
                </div>
              </div>
              <p className="small mt-12 clamp-3">{s.description}</p>
              <div className="meta-row">
                {s.price_info && <span className="bold" style={{ color: 'var(--ink)' }}>{s.price_info}</span>}
                {s.neighborhood && <span><MapPin size={13} /> {s.neighborhood.name}</span>}
              </div>
              <div className="flex gap-8 mt-12 wrap">
                <a href={`tel:${s.contact_phone}`} className="btn btn-primary btn-sm"><Phone size={15} /> {s.contact_phone}</a>
                {s.provider_id !== profile.id && <button className="btn btn-secondary btn-sm" onClick={() => message(s.provider_id)}><MessageCircle size={15} /> Message</button>}
                {s.provider_id !== profile.id && <button className="btn btn-ghost btn-sm" onClick={() => setReport(s.id)}>Report</button>}
                {(s.provider_id === profile.id || isAdmin) && <button className="btn btn-ghost btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(s)}><Trash2 size={15} /></button>}
              </div>
            </div>
          ))}
        </div>
      )}
      {creating && <ServiceForm categories={categories} onClose={() => setCreating(false)} onSaved={() => { setCreating(false); load() }} />}
      {report && <ReportModal targetType="service" targetId={report} onClose={() => setReport(null)} />}
    </>
  )
}

function ServiceForm({ categories, onClose, onSaved }) {
  const { profile } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState({ service_category_id: categories[0]?.id || '', service_name: '', description: '', contact_phone: profile.phone || '', contact_email: '', price_info: '' })
  const [loc, setLoc] = useState({ latitude: null, longitude: null, location_name: '' })
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const save = async () => {
    if (!form.service_name.trim() || !form.description.trim() || !form.contact_phone.trim() || !form.service_category_id) return setError('Name, category, description and phone are required.')
    setBusy(true); setError('')
    try {
      const image_url = file ? await uploadImage(file, 'service') : null
      await createService({
        service_category_id: Number(form.service_category_id), service_name: form.service_name.trim(), description: form.description.trim(),
        contact_phone: form.contact_phone.trim(), contact_email: form.contact_email.trim() || null, price_info: form.price_info.trim() || null,
        image_url, latitude: loc.latitude, longitude: loc.longitude, neighborhood_id: profile.neighborhood_id,
      })
      toast('Service listed. An admin can verify it.')
      onSaved()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <Modal title="List your service" onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Publish'}</button></>}>
      <ErrorText error={error} />
      <div className="row">
        <Field label="Category">
          <select className="select" value={form.service_category_id} onChange={set('service_category_id')}>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
        </Field>
        <Field label="Price info"><input className="input" value={form.price_info} onChange={set('price_info')} placeholder="From 300 BDT" maxLength={100} /></Field>
      </div>
      <Field label="Service name"><input className="input" value={form.service_name} onChange={set('service_name')} maxLength={150} /></Field>
      <Field label="Description"><textarea className="textarea" value={form.description} onChange={set('description')} maxLength={3000} /></Field>
      <div className="row">
        <Field label="Phone"><input className="input" value={form.contact_phone} onChange={set('contact_phone')} maxLength={20} /></Field>
        <Field label="Email (optional)"><input className="input" type="email" value={form.contact_email} onChange={set('contact_email')} /></Field>
      </div>
      <Field label="Photo / logo"><ImagePicker file={file} onChange={setFile} /></Field>
      <LocationField value={loc} onChange={setLoc} nameLabel="Service area (optional)" placeholder="e.g. Ashulia & Savar" />
    </Modal>
  )
}

/* ------------------------- Help requests ------------------------- */
function HelpBoard({ highlight }) {
  const { profile, isAdmin } = useAuth()
  const toast = useToast()
  const [status, setStatus] = useState('active')
  const [items, setItems] = useState(null)
  const [creating, setCreating] = useState(false)
  const [replies, setReplies] = useState({})

  const load = () => getHelpRequests({ status }).then(setItems).catch(() => setItems([]))
  useEffect(() => { setItems(null); load() }, [status]) // eslint-disable-line react-hooks/exhaustive-deps

  const offer = async (h) => {
    const msg = (replies[h.id] || '').trim()
    if (!msg) return
    try { await respondToHelp(h.id, msg); setReplies((r) => ({ ...r, [h.id]: '' })); toast('Thanks for helping! 💚'); load() } catch (e) { toast(e.message, 'error') }
  }
  const setHelpStatus = async (h, s) => { try { await updateHelpRequest(h.id, { status: s }); load() } catch (e) { toast(e.message, 'error') } }
  const remove = async (h) => {
    if (!confirm('Delete this request?')) return
    try { await deleteHelpRequest(h.id); load() } catch (e) { toast(e.message, 'error') }
  }

  return (
    <>
      <div className="toolbar">
        <div className="chips">
          <button className={`chip ${status === 'active' ? 'active' : ''}`} onClick={() => setStatus('active')}>Needs help</button>
          <button className={`chip ${status === 'resolved' ? 'active' : ''}`} onClick={() => setStatus('resolved')}>Resolved</button>
        </div>
        <button className="btn btn-primary" style={{ marginLeft: 'auto' }} onClick={() => setCreating(true)}><Plus size={18} /> Ask for help</button>
      </div>
      {items === null ? <Spinner /> : items.length === 0 ? (
        <div className="card"><Empty icon={HandHeart} title="No requests right now" text="Need groceries, a ride or a hand with repairs? Ask your neighbors." /></div>
      ) : (
        <div className="list">
          {items.map((h) => {
            const mine = h.user_id === profile.id
            return (
              <div key={h.id} className="card card-pad" style={String(h.id) === highlight ? { borderColor: 'var(--accent)', boxShadow: '0 0 0 3px rgba(224,102,58,.15)' } : undefined}>
                <div className="flex gap-12">
                  <Link to={`/profile/${h.author?.username}`}><Avatar src={h.author?.avatar_url} name={h.author?.full_name} size={44} /></Link>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="flex gap-6 items-center wrap">
                      <span className="bold">{h.author?.full_name}</span>
                      <span className="small muted">· {timeAgo(h.created_at)}</span>
                      <span className="pill amber">{labelize(h.help_type)}</span>
                      <StatusPill value={h.status} />
                    </div>
                    <h4 className="mt-8">{h.title}</h4>
                    <p className="small mt-8" style={{ whiteSpace: 'pre-wrap' }}>{h.details}</p>
                  </div>
                </div>

                {h.help_responses?.length > 0 && (
                  <div className="mt-12" style={{ marginLeft: 56 }}>
                    {h.help_responses.map((r) => (
                      <div key={r.id} className="comment" style={{ marginTop: 6 }}>
                        <Avatar src={r.responder?.avatar_url} name={r.responder?.full_name} size={28} />
                        <div className="comment-bubble"><div className="who">{r.responder?.full_name} <span className="muted" style={{ fontWeight: 400 }}>offered help · {timeAgo(r.created_at)}</span></div><div className="text">{r.message}</div></div>
                      </div>
                    ))}
                  </div>
                )}

                <div className="flex gap-8 mt-12 wrap" style={{ marginLeft: 56 }}>
                  {!mine && h.status !== 'resolved' && h.status !== 'closed' && (
                    <form className="comment-input" style={{ flex: 1, minWidth: 220 }} onSubmit={(e) => { e.preventDefault(); offer(h) }}>
                      <input placeholder="I can help — write a message…" value={replies[h.id] || ''} onChange={(e) => setReplies((r) => ({ ...r, [h.id]: e.target.value }))} maxLength={2000} />
                      <button type="submit" disabled={!(replies[h.id] || '').trim()}><HandHeart size={18} /></button>
                    </form>
                  )}
                  {(mine || isAdmin) && h.status !== 'resolved' && <button className="btn btn-secondary btn-sm" onClick={() => setHelpStatus(h, 'resolved')}><CheckCircle2 size={15} /> Mark resolved</button>}
                  {(mine || isAdmin) && h.status === 'resolved' && <button className="btn btn-secondary btn-sm" onClick={() => setHelpStatus(h, 'open')}>Reopen</button>}
                  {(mine || isAdmin) && <button className="btn btn-ghost btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(h)}><Trash2 size={15} /></button>}
                </div>
              </div>
            )
          })}
        </div>
      )}
      {creating && <HelpForm onClose={() => setCreating(false)} onSaved={() => { setCreating(false); load() }} />}
    </>
  )
}

function HelpForm({ onClose, onSaved }) {
  const { profile } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState({ help_type: 'grocery', title: '', details: '' })
  const [loc, setLoc] = useState({ latitude: null, longitude: null, location_name: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const save = async () => {
    if (!form.title.trim() || !form.details.trim()) return setError('Add a title and details.')
    setBusy(true); setError('')
    try {
      await createHelpRequest({ ...form, title: form.title.trim(), details: form.details.trim(), latitude: loc.latitude, longitude: loc.longitude, neighborhood_id: profile.neighborhood_id })
      toast('Your neighbors have been asked for help')
      onSaved()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <Modal title="Ask your neighbors for help" onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Posting…' : 'Post request'}</button></>}>
      <ErrorText error={error} />
      <Field label="What kind of help?">
        <div className="chips">{HELP_TYPES.map((t) => <button key={t} type="button" className={`chip ${form.help_type === t ? 'active' : ''}`} onClick={() => setForm((f) => ({ ...f, help_type: t }))}>{labelize(t)}</button>)}</div>
      </Field>
      <Field label="Title"><input className="input" value={form.title} onChange={set('title')} maxLength={200} placeholder="e.g. Need a ride to the hospital tomorrow" /></Field>
      <Field label="Details"><textarea className="textarea" value={form.details} onChange={set('details')} maxLength={5000} /></Field>
      <LocationField value={loc} onChange={setLoc} nameLabel="Where (optional)" />
    </Modal>
  )
}

import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Baby, BookOpen, Car, CookingPot, Dumbbell, Gift, MapPin, Package, Plus, Repeat2, Search, Shirt, Smartphone, Sofa, Store, Tag, Trash2,
} from 'lucide-react'
import { createListing, deleteListing, getListing, getListings, updateListing, uploadImage } from '../lib/api'
import { CONDITIONS, LISTING_CATEGORIES, LISTING_TYPES } from '../lib/constants'
import { formatPrice, labelize, timeAgo } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Empty, ErrorText, Field, Modal, Spinner, StatusPill } from '../components/ui'
import { ImagePicker, LocationField } from '../components/FormBits'
import CommentSection from '../components/CommentSection'
import OwnerBar from '../components/OwnerBar'

const TYPE_ICONS = { '': Store, sell: Tag, buy: Search, exchange: Repeat2, free: Gift }
const CATEGORY_ICONS = {
  Electronics: Smartphone, Furniture: Sofa, Vehicles: Car, Books: BookOpen, Clothing: Shirt,
  'Home & Kitchen': CookingPot, Kids: Baby, Sports: Dumbbell, Other: Package,
}

export default function Marketplace() {
  const [params, setParams] = useSearchParams()
  const [items, setItems] = useState(null)
  const [type, setType] = useState('')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [creating, setCreating] = useState(false)
  const openId = params.get('item')

  const load = () => getListings({ type: type || undefined, category: category || undefined, search: q || undefined }).then(setItems).catch(() => setItems([]))
  useEffect(() => { setItems(null); load() }, [type, category, q]) // eslint-disable-line react-hooks/exhaustive-deps

  const typeLabel = { '': 'Browse all', sell: 'For sale', buy: 'Wanted', exchange: 'Exchange', free: 'Free stuff' }
  const heading = category || (type ? typeLabel[type] : "Today's picks")

  return (
    <div className="mp-layout">
      <aside className="mp-side">
        <h2>Buy & sell</h2>
        <form className="fb-search" onSubmit={(e) => { e.preventDefault(); setQ(search.trim()) }}>
          <Search size={16} color="var(--muted)" />
          <input placeholder="Search Marketplace" value={search} onChange={(e) => setSearch(e.target.value)} />
        </form>
        {Object.entries(typeLabel).map(([t, label]) => {
          const Icon = TYPE_ICONS[t]
          return (
            <button key={label} className={`mp-cat ${type === t && !category ? 'active' : ''}`} onClick={() => { setType(t); setCategory('') }}>
              <span className="ic"><Icon size={18} /></span>{label}
            </button>
          )
        })}
        <div style={{ padding: '8px' }}>
          <button className="btn btn-primary btn-block" style={{ background: 'var(--green-100)', color: 'var(--green-700)' }} onClick={() => setCreating(true)}><Plus size={18} /> Create new listing</button>
        </div>
        <div className="side-sep" />
        <div className="side-title">Categories</div>
        {LISTING_CATEGORIES.map((c) => {
          const Icon = CATEGORY_ICONS[c] || Package
          return (
            <button key={c} className={`mp-cat ${category === c ? 'active' : ''}`} onClick={() => { setCategory(category === c ? '' : c); setType('') }}>
              <span className="ic"><Icon size={18} /></span>{c}
            </button>
          )
        })}
      </aside>

      <main className="mp-main">
        <div className="flex between items-center wrap gap-8" style={{ marginBottom: 12 }}>
          <h3 style={{ margin: 0 }}>{q ? `Results for "${q}"` : heading}</h3>
          {(q || category || type) && <button className="btn btn-secondary btn-sm" onClick={() => { setQ(''); setSearch(''); setCategory(''); setType('') }}>Clear filters</button>}
        </div>

        {items === null ? <Spinner /> : items.length === 0 ? (
          <div className="card"><Empty icon={Tag} title="No listings yet" text="Selling something or giving it away? List it for your neighbors." action={<button className="btn btn-primary" onClick={() => setCreating(true)}>Create listing</button>} /></div>
        ) : (
          <div className="grid-cards">
            {items.map((l) => (
              <div key={l.id} className="item-card" onClick={() => setParams({ item: l.id })}>
                <div className="item-thumb">
                  {l.image_url ? <img src={l.image_url} alt="" loading="lazy" /> : <Tag size={40} />}
                  {l.listing_type !== 'sell' && <span className="pill dark">{l.listing_type === 'buy' ? 'Wanted' : labelize(l.listing_type)}</span>}
                </div>
                <div className="item-info">
                  <div className="item-price">{formatPrice(l.price, l.listing_type)}</div>
                  <div className="item-title">{l.title}</div>
                  <div className="item-sub">{l.location_name || l.neighborhood?.name || 'Nearby'} · {timeAgo(l.created_at)}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>

      {creating && <ListingForm onClose={() => setCreating(false)} onSaved={() => { setCreating(false); load() }} />}
      {openId && <ListingDetail id={Number(openId)} onClose={() => setParams({})} onChanged={load} />}
    </div>
  )
}

function ListingDetail({ id, onClose, onChanged }) {
  const { profile, isAdmin } = useAuth()
  const toast = useToast()
  const [item, setItem] = useState(undefined)
  const [editing, setEditing] = useState(false)
  const load = () => getListing(id).then(setItem).catch(() => setItem(null))
  useEffect(() => { load() }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (editing) return <ListingForm listing={item} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); load(); onChanged() }} />

  const mine = item && item.user_id === profile.id
  const setStatus = async (status) => {
    try { await updateListing(id, { status }); toast(`Marked as ${status}`); load(); onChanged() } catch (e) { toast(e.message, 'error') }
  }
  const remove = async () => {
    if (!confirm('Delete this listing?')) return
    try { await deleteListing(id); toast('Listing deleted'); onChanged(); onClose() } catch (e) { toast(e.message, 'error') }
  }

  return (
    <Modal title="Listing" onClose={onClose} wide>
      {item === undefined ? <Spinner /> : item === null ? <Empty icon={Tag} title="Listing not found" /> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 20 }} className="detail-grid">
          <div className="item-thumb" style={{ borderRadius: 12, aspectRatio: '1 / 1' }}>
            {item.image_url ? <img src={item.image_url} alt="" /> : <Tag size={48} />}
          </div>
          <div>
            <div className="flex gap-6 wrap mb-12"><StatusPill value={item.status} /><span className="pill">{item.listing_type === 'buy' ? 'Wanted' : labelize(item.listing_type)}</span>{item.category && <span className="pill blue">{item.category}</span>}</div>
            <h3 style={{ fontSize: 22 }}>{item.title}</h3>
            <div className="item-price" style={{ fontSize: 24, margin: '6px 0' }}>{formatPrice(item.price, item.listing_type)}</div>
            {item.item_condition && <div className="small muted">Condition: <b>{labelize(item.item_condition)}</b></div>}
            {(item.location_name || item.neighborhood) && <div className="small muted mt-8"><MapPin size={13} /> {item.location_name || item.neighborhood?.name}</div>}
            <p className="mt-12" style={{ whiteSpace: 'pre-wrap' }}>{item.description}</p>
            {item.contact_info && <p className="small mt-8">Contact: <b>{item.contact_info}</b></p>}
            <div className="divider" />
            <OwnerBar person={item.author} createdAt={item.created_at} label="Seller" reportType="listing" reportId={item.id} messageText="Message seller" />
            {(mine || isAdmin) && (
              <div className="flex gap-8 wrap mt-8">
                {mine && <button className="btn btn-secondary btn-sm" onClick={() => setEditing(true)}>Edit</button>}
                {item.status !== 'sold' && <button className="btn btn-secondary btn-sm" onClick={() => setStatus('sold')}>Mark sold</button>}
                {item.status === 'available' && <button className="btn btn-secondary btn-sm" onClick={() => setStatus('reserved')}>Mark reserved</button>}
                {item.status !== 'available' && <button className="btn btn-secondary btn-sm" onClick={() => setStatus('available')}>Mark available</button>}
                <button className="btn btn-ghost btn-sm" style={{ color: 'var(--red)' }} onClick={remove}><Trash2 size={15} /> Delete</button>
              </div>
            )}
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <div className="bold mb-12">Questions for the seller</div>
            <CommentSection target={{ listing_id: item.id }} placeholder="Ask a question…" />
          </div>
        </div>
      )}
      <style>{`@media (max-width: 700px) { .detail-grid { grid-template-columns: 1fr !important; } }`}</style>
    </Modal>
  )
}

function ListingForm({ listing, onClose, onSaved }) {
  const { profile } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState({
    listing_type: listing?.listing_type || 'sell', title: listing?.title || '', description: listing?.description || '',
    category: listing?.category || '', item_condition: listing?.item_condition || 'used', price: listing?.price ?? '', contact_info: listing?.contact_info || '',
  })
  const [loc, setLoc] = useState({ latitude: listing?.latitude ?? null, longitude: listing?.longitude ?? null, location_name: listing?.location_name || '' })
  const [file, setFile] = useState(null)
  const [keep, setKeep] = useState(listing?.image_url || null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const save = async () => {
    if (!form.title.trim() || !form.description.trim()) return setError('Add a title and description.')
    setBusy(true); setError('')
    try {
      const image_url = file ? await uploadImage(file, 'listing') : keep
      const values = {
        ...form, title: form.title.trim(), description: form.description.trim(),
        category: form.category || null, contact_info: form.contact_info.trim() || null,
        price: form.listing_type === 'free' || form.price === '' ? null : Number(form.price),
        item_condition: form.item_condition || null, image_url,
        location_name: loc.location_name?.trim() || null, latitude: loc.latitude, longitude: loc.longitude,
        neighborhood_id: listing?.neighborhood_id ?? profile.neighborhood_id,
      }
      if (listing) await updateListing(listing.id, values)
      else await createListing(values)
      toast(listing ? 'Listing updated' : 'Listing published')
      onSaved()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <Modal title={listing ? 'Edit listing' : 'Create listing'} onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : listing ? 'Save' : 'Publish'}</button></>}>
      <ErrorText error={error} />
      <Field label="Type">
        <div className="chips">
          {LISTING_TYPES.map((t) => <button key={t} type="button" className={`chip ${form.listing_type === t ? 'active' : ''}`} onClick={() => setForm((f) => ({ ...f, listing_type: t }))}>{t === 'buy' ? 'Looking to buy' : labelize(t)}</button>)}
        </div>
      </Field>
      <Field label="Photo"><ImagePicker file={file} existingUrl={keep} onChange={(f) => { setFile(f); if (!f) setKeep(null) }} /></Field>
      <Field label="Title"><input className="input" value={form.title} onChange={set('title')} maxLength={200} placeholder="What are you selling?" /></Field>
      <div className="row">
        {form.listing_type !== 'free' && <Field label="Price (৳)"><input className="input" type="number" min="0" value={form.price} onChange={set('price')} /></Field>}
        <Field label="Category">
          <select className="select" value={form.category} onChange={set('category')}><option value="">Choose…</option>{LISTING_CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select>
        </Field>
        <Field label="Condition">
          <select className="select" value={form.item_condition} onChange={set('item_condition')}>{CONDITIONS.map((c) => <option key={c} value={c}>{labelize(c)}</option>)}</select>
        </Field>
      </div>
      <Field label="Description"><textarea className="textarea" value={form.description} onChange={set('description')} maxLength={5000} /></Field>
      <Field label="Contact (optional)" hint="Neighbors can always message you in the app."><input className="input" value={form.contact_info} onChange={set('contact_info')} maxLength={120} /></Field>
      <LocationField value={loc} onChange={setLoc} nameLabel="Pick-up location" />
    </Modal>
  )
}

import { useEffect, useState } from 'react'
import { ImagePlus, MapPin, Megaphone, Smile } from 'lucide-react'
import { createPost, getNeighborhoods, getPostCategories, updatePost, uploadImage } from '../lib/api'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Avatar, ErrorText, Modal } from './ui'
import { ImagePicker, LocationField } from './FormBits'
import { t } from '../lib/i18n'

// The "What's on your mind?" box at the top of the feed
export function ComposerBox({ onPosted }) {
  const { profile } = useAuth()
  const [open, setOpen] = useState(null)
  const first = profile.full_name.split(' ')[0]
  return (
    <div className="card composer">
      <div className="composer-top">
        <Avatar src={profile.avatar_url} name={profile.full_name} size={42} />
        <button className="composer-fake" onClick={() => setOpen('text')}>{t('What\'s happening around you,')} {first}?</button>
      </div>
      <div className="composer-actions">
        <button onClick={() => setOpen('photo')}><ImagePlus size={20} color="#3d7457" /> {t('Photo')}</button>
        <button onClick={() => setOpen('location')}><MapPin size={20} color="#e0663a" /> {t('Location')}</button>
        <button onClick={() => setOpen('text')}><Smile size={20} color="#b7791f" /> {t('Feeling / ask')}</button>
      </div>
      {open && <PostModal mode={open} onClose={() => setOpen(null)} onSaved={onPosted} />}
    </div>
  )
}

// Create or edit a post
export function PostModal({ mode = 'text', post, onClose, onSaved }) {
  const { profile, isAdmin } = useAuth()
  const toast = useToast()
  const editing = Boolean(post)
  const [body, setBody] = useState(post?.body || '')
  const [title, setTitle] = useState(post?.title || '')
  const [file, setFile] = useState(null)
  const [keepImage, setKeepImage] = useState(post?.image_url || null)
  const [showPhoto, setShowPhoto] = useState(mode === 'photo' || Boolean(post?.image_url))
  const [showLoc, setShowLoc] = useState(mode === 'location' || Boolean(post?.latitude || post?.location_name))
  const [loc, setLoc] = useState({ latitude: post?.latitude ?? null, longitude: post?.longitude ?? null, location_name: post?.location_name || '' })
  const [categoryId, setCategoryId] = useState(post?.category_id || '')
  const [neighborhoodId, setNeighborhoodId] = useState(post?.neighborhood_id ?? profile.neighborhood_id ?? '')
  const [announcement, setAnnouncement] = useState(post?.post_type === 'announcement')
  const [categories, setCategories] = useState([])
  const [neighborhoods, setNeighborhoods] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    getPostCategories().then(setCategories).catch(() => {})
    getNeighborhoods().then(setNeighborhoods).catch(() => {})
  }, [])

  const canSubmit = body.trim() || file || keepImage || post?.shared_post_id

  const submit = async () => {
    if (!canSubmit) return
    setBusy(true); setError('')
    try {
      const image_url = file ? await uploadImage(file, 'post') : keepImage
      const values = {
        body: body.trim(),
        title: announcement && title.trim() ? title.trim() : null,
        image_url: image_url || null,
        category_id: categoryId ? Number(categoryId) : null,
        neighborhood_id: neighborhoodId ? Number(neighborhoodId) : null,
        location_name: showLoc ? loc.location_name?.trim() || null : null,
        latitude: showLoc ? loc.latitude : null,
        longitude: showLoc ? loc.longitude : null,
      }
      if (isAdmin) values.post_type = announcement ? 'announcement' : 'post'
      if (editing) await updatePost(post.id, values)
      else await createPost(values)
      toast(editing ? t('Post updated') : t('Posted to your neighborhood'))
      onSaved?.()
      onClose()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={editing ? t('Edit post') : t('Create post')} onClose={onClose}
      footer={<button className="btn btn-primary btn-block" disabled={!canSubmit || busy} onClick={submit}>{busy ? t('Posting…') : editing ? t('Save') : t('Post')}</button>}>
      <ErrorText error={error} />
      <div className="flex gap-12 items-center mb-12">
        <Avatar src={profile.avatar_url} name={profile.full_name} size={44} />
        <div style={{ flex: 1 }}>
          <div className="bold">{profile.full_name}</div>
          <div className="flex gap-6 mt-8 wrap">
            <select className="select" style={{ width: 'auto', padding: '4px 8px', fontSize: 13 }} value={neighborhoodId} onChange={(e) => setNeighborhoodId(e.target.value)}>
              <option value="">{t('🌐 All neighborhoods')}</option>
              {neighborhoods.map((n) => <option key={n.id} value={n.id}>🏘️ {n.name}</option>)}
            </select>
            <select className="select" style={{ width: 'auto', padding: '4px 8px', fontSize: 13 }} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">{t('No category')}</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
        </div>
      </div>

      {isAdmin && (
        <label className="flex items-center gap-8 mb-12" style={{ cursor: 'pointer' }}>
          <input type="checkbox" checked={announcement} onChange={(e) => setAnnouncement(e.target.checked)} />
          <Megaphone size={16} /> <span className="bold small">{t('Post as official announcement')}</span>
        </label>
      )}
      {announcement && (
        <input className="input mb-12" placeholder={t('Announcement title')} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
      )}

      <textarea className="composer-textarea" autoFocus placeholder={`What's happening around you, ${profile.full_name.split(' ')[0]}?`}
        value={body} onChange={(e) => setBody(e.target.value)} maxLength={5000} />

      {showPhoto && (
        <ImagePicker file={file} existingUrl={keepImage}
          onChange={(f) => { setFile(f); if (!f) setKeepImage(null) }} />
      )}
      {showLoc && <div className="mt-12"><LocationField value={loc} onChange={setLoc} nameLabel={t('Where is this?')} /></div>}

      <div className="composer-tools">
        <span className="bold small">{t('Add to your post')}</span>
        <div className="tools">
          <button type="button" className="icon-btn ghost" onClick={() => setShowPhoto(true)} title={t('Photo')}><ImagePlus size={22} color="#3d7457" /></button>
          <button type="button" className="icon-btn ghost" onClick={() => setShowLoc(true)} title={t('Location')}><MapPin size={22} color="#e0663a" /></button>
        </div>
      </div>
    </Modal>
  )
}

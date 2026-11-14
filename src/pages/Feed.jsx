import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { LocateFixed, Search, X } from 'lucide-react'
import { getNeighborhoods, getPostCategories, searchPeople } from '../lib/api'
import { getCurrentPosition } from '../lib/geo'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ComposerBox } from '../components/Composer'
import FeedList from '../components/FeedList'
import { Avatar } from '../components/ui'
import { Link } from 'react-router-dom'
import { RightRail } from './Home'

export default function Feed() {
  const { profile } = useAuth()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') || ''
  const [search, setSearch] = useState(q)
  const [categories, setCategories] = useState([])
  const [neighborhoods, setNeighborhoods] = useState([])
  const [categoryId, setCategoryId] = useState('')
  const [neighborhoodId, setNeighborhoodId] = useState('')
  const [near, setNear] = useState(null)
  const [radius, setRadius] = useState(3)
  const [people, setPeople] = useState([])
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    getPostCategories().then(setCategories).catch(() => {})
    getNeighborhoods().then(setNeighborhoods).catch(() => {})
  }, [])
  useEffect(() => { setSearch(q) }, [q])
  useEffect(() => {
    if (!q) { setPeople([]); return }
    searchPeople(q, 5).then(setPeople).catch(() => {})
  }, [q])

  const nearMe = async () => {
    if (near) { setNear(null); return }
    try { setNear(await getCurrentPosition()) } catch (e) { toast(e.message, 'error') }
  }

  const filters = {
    search: q || undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
    neighborhoodId: neighborhoodId ? Number(neighborhoodId) : undefined,
    near: near || undefined,
    radiusKm: near ? radius : undefined,
  }

  return (
    <div className="page">
      <div className="with-rail">
        <div className="feed-col">
          <div className="card card-pad">
            <form className="toolbar" style={{ marginBottom: 12 }} onSubmit={(e) => { e.preventDefault(); setParams(search.trim() ? { q: search.trim() } : {}) }}>
              <div className="search" style={{ maxWidth: 'none', flex: 1 }}>
                <Search size={18} color="var(--muted)" />
                <input placeholder="Search posts" value={search} onChange={(e) => setSearch(e.target.value)} />
                {q && <button type="button" className="link" onClick={() => setParams({})}><X size={16} /></button>}
              </div>
              <button className="btn btn-primary" type="submit">Search</button>
            </form>
            <div className="flex gap-8 wrap items-center">
              <select className="select" style={{ width: 'auto' }} value={neighborhoodId} onChange={(e) => setNeighborhoodId(e.target.value)}>
                <option value="">All neighborhoods</option>
                {neighborhoods.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
              </select>
              <button className={`chip ${near ? 'active' : ''}`} onClick={nearMe}><LocateFixed size={14} /> Near me</button>
              {near && (
                <select className="select" style={{ width: 'auto' }} value={radius} onChange={(e) => setRadius(Number(e.target.value))}>
                  {[1, 3, 5, 10].map((r) => <option key={r} value={r}>Within {r} km</option>)}
                </select>
              )}
            </div>
            <div className="chips mt-12">
              <button className={`chip ${!categoryId ? 'active' : ''}`} onClick={() => setCategoryId('')}>All</button>
              {categories.map((c) => (
                <button key={c.id} className={`chip ${String(categoryId) === String(c.id) ? 'active' : ''}`} onClick={() => setCategoryId(c.id)}>{c.name}</button>
              ))}
            </div>
          </div>

          {people.length > 0 && (
            <div className="card card-pad">
              <div className="card-title mb-12">People</div>
              <div className="mini-list">
                {people.map((p) => (
                  <Link key={p.id} to={`/profile/${p.username}`} className="mini-item">
                    <Avatar src={p.avatar_url} name={p.full_name} size={40} />
                    <div><div className="t">{p.full_name}</div><div className="s">@{p.username}{p.neighborhood?.name ? ` · ${p.neighborhood.name}` : ''}</div></div>
                  </Link>
                ))}
              </div>
            </div>
          )}

          {!q && <ComposerBox onPosted={() => setReloadKey((k) => k + 1)} />}
          <FeedList filters={filters} reloadKey={reloadKey}
            emptyText={near ? 'No pinned posts near you yet. Add a location when you post so neighbors can find it.' : q ? `No posts match "${q}".` : undefined} />
        </div>
        {profile && <RightRail />}
      </div>
    </div>
  )
}

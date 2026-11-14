import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { ComposerBox, PostModal } from '../components/Composer'
import FeedList from '../components/FeedList'
import LeftNav from '../components/LeftNav'
import NearbyStrip from '../components/NearbyStrip'
import RightRail from '../components/RightRail'

export default function Home() {
  const { profile } = useAuth()
  const [reloadKey, setReloadKey] = useState(0)
  const [scope, setScope] = useState(profile.neighborhood_id ? 'mine' : 'all')
  const [composing, setComposing] = useState(false)

  const filters = scope === 'mine' && profile.neighborhood_id ? { neighborhoodId: profile.neighborhood_id } : {}
  const reload = () => setReloadKey((k) => k + 1)

  return (
    <div className="fb-layout">
      <LeftNav />
      <main className="fb-center">
        <div className="feed-col">
          <NearbyStrip onCreate={() => setComposing(true)} />
          <ComposerBox onPosted={reload} />
          {profile.neighborhood_id && (
            <div className="chips">
              <button className={`chip ${scope === 'mine' ? 'active' : ''}`} onClick={() => setScope('mine')}>🏘️ {profile.neighborhood?.name}</button>
              <button className={`chip ${scope === 'all' ? 'active' : ''}`} onClick={() => setScope('all')}>🌐 All neighborhoods</button>
            </div>
          )}
          <FeedList filters={filters} reloadKey={reloadKey} />
        </div>
      </main>
      <RightRail />
      {composing && <PostModal mode="photo" onClose={() => setComposing(false)} onSaved={reload} />}
    </div>
  )
}

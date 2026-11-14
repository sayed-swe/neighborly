import { useCallback, useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { supabase } from '../lib/supabase'
import { countUnreadNotifications, getAlerts, getConversations } from '../lib/api'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function AppLayout() {
  const { user } = useAuth()
  const toast = useToast()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [counts, setCounts] = useState({ notifications: 0, messages: 0, alerts: 0 })

  const refreshCounts = useCallback(async () => {
    try {
      const [notifications, convos, alerts] = await Promise.all([
        countUnreadNotifications(),
        getConversations(),
        getAlerts({ status: 'active' }),
      ])
      setCounts({
        notifications,
        messages: (convos || []).reduce((sum, c) => sum + Number(c.unread_count || 0), 0),
        alerts: alerts?.length || 0,
      })
    } catch (e) {
      console.error(e)
    }
  }, [])

  useEffect(() => { refreshCounts() }, [refreshCounts, location.pathname])
  useEffect(() => { setMenuOpen(false) }, [location.pathname])

  // Live updates: new notifications and messages
  useEffect(() => {
    if (!user) return
    const channel = supabase
      .channel(`live-${user.id}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'notifications', filter: `user_id=eq.${user.id}` }, (payload) => {
        if (payload.new?.type === 'emergency') toast(payload.new.message, 'error')
        refreshCounts()
      })
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages' }, () => refreshCounts())
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [user, refreshCounts, toast])

  return (
    <div className="app-shell">
      <Sidebar open={menuOpen} counts={counts} />
      <div className={`scrim ${menuOpen ? 'open' : ''}`} onClick={() => setMenuOpen(false)} />
      <div className="main-area">
        <Topbar counts={counts} onMenu={() => setMenuOpen(true)} />
        <Outlet context={{ refreshCounts, counts }} />
      </div>
    </div>
  )
}

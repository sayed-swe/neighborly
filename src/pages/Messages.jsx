import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { ArrowLeft, MessageCircle, SquarePen, Search, SendHorizontal } from 'lucide-react'
import { supabase } from '../lib/supabase'
import {
  getConversationMembers, getConversations, getMessages, markConversationRead, searchPeople, sendMessage, startConversation,
} from '../lib/api'
import { timeAgo } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Avatar, Empty, Modal, Spinner } from '../components/ui'

export default function Messages() {
  const { id } = useParams()
  const conversationId = id ? Number(id) : null
  const { profile } = useAuth()
  const { refreshCounts } = useOutletContext() || {}
  const navigate = useNavigate()
  const [convos, setConvos] = useState(null)
  const [filter, setFilter] = useState('')
  const [newChat, setNewChat] = useState(false)

  const loadConvos = () => getConversations().then(setConvos).catch(() => setConvos([]))
  useEffect(() => { loadConvos() }, [conversationId])

  // refresh the inbox when any message arrives
  useEffect(() => {
    const ch = supabase.channel(`inbox-${profile.id}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages' }, () => loadConvos())
      .subscribe()
    return () => { supabase.removeChannel(ch) }
  }, [profile.id])

  const shown = (convos || []).filter((c) => !filter || c.other_name?.toLowerCase().includes(filter.toLowerCase()))

  return (
    <div className={`chat ${conversationId ? 'has-thread' : ''}`}>
      <div className="chat-list">
        <div className="chat-list-head">
          <div className="flex between items-center">
            <h3 style={{ fontSize: 22, fontWeight: 700 }}>Chats</h3>
            <button className="icon-btn" onClick={() => setNewChat(true)} aria-label="New message"><SquarePen size={18} /></button>
          </div>
          <div className="search" style={{ maxWidth: 'none' }}><Search size={16} color="var(--muted)" /><input placeholder="Search chats" value={filter} onChange={(e) => setFilter(e.target.value)} /></div>
        </div>
        {convos === null ? <Spinner /> : shown.length === 0 ? (
          <Empty icon={MessageCircle} title="No conversations" text="Message a neighbor from their profile, a listing or a service." />
        ) : shown.map((c) => (
          <div key={c.conversation_id} className={`chat-row ${c.conversation_id === conversationId ? 'active' : ''} ${Number(c.unread_count) > 0 ? 'unread' : ''}`}
            onClick={() => navigate(`/messages/${c.conversation_id}`)}>
            <Avatar src={c.other_avatar} name={c.other_name} size={50} />
            <div className="grow">
              <div className="t"><span>{c.other_name}</span><small>{timeAgo(c.last_message_at)}</small></div>
              <div className="s">{c.last_sender_id === profile.id ? 'You: ' : ''}{c.last_message || 'Say hello 👋'}</div>
            </div>
            {Number(c.unread_count) > 0 && <span className="nav-badge hot" style={{ marginLeft: 0 }}>{c.unread_count}</span>}
          </div>
        ))}
      </div>

      <div className="chat-thread">
        {conversationId ? (
          <Thread key={conversationId} conversationId={conversationId} onActivity={() => { loadConvos(); refreshCounts?.() }} />
        ) : (
          <div style={{ margin: 'auto' }}><Empty icon={MessageCircle} title="Your messages" text="Pick a conversation or start a new one." action={<button className="btn btn-primary" onClick={() => setNewChat(true)}>New message</button>} /></div>
        )}
      </div>

      {newChat && <NewChatModal onClose={() => setNewChat(false)} />}
    </div>
  )
}

function Thread({ conversationId, onActivity }) {
  const { profile } = useAuth()
  const toast = useToast()
  const [messages, setMessages] = useState(null)
  const [other, setOther] = useState(null)
  const [text, setText] = useState('')
  const bottom = useRef(null)

  useEffect(() => {
    getMessages(conversationId).then(setMessages).catch((e) => { toast(e.message, 'error'); setMessages([]) })
    getConversationMembers(conversationId).then((m) => setOther(m.find((x) => x.user_id !== profile.id)?.user || null)).catch(() => {})
    markConversationRead(conversationId).then(onActivity).catch(() => {})
    const ch = supabase.channel(`conv-${conversationId}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages', filter: `conversation_id=eq.${conversationId}` }, (payload) => {
        setMessages((list) => (list?.some((m) => m.id === payload.new.id) ? list : [...(list || []), payload.new]))
        markConversationRead(conversationId).catch(() => {})
      })
      .subscribe()
    return () => { supabase.removeChannel(ch) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId])

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  const send = async (e) => {
    e.preventDefault()
    const body = text.trim()
    if (!body) return
    setText('')
    try {
      const m = await sendMessage(conversationId, body)
      setMessages((list) => (list?.some((x) => x.id === m.id) ? list : [...(list || []), m]))
      onActivity()
    } catch (err) { toast(err.message, 'error'); setText(body) }
  }

  // show a timestamp when more than 15 minutes pass between messages
  const withTimes = (messages || []).map((m, i, arr) => ({
    ...m, showTime: i === 0 || new Date(m.created_at) - new Date(arr[i - 1].created_at) > 15 * 60 * 1000,
  }))

  return (
    <>
      <div className="chat-thread-head">
        <Link to="/messages" className="icon-btn ghost menu-toggle" aria-label="Back"><ArrowLeft size={20} /></Link>
        {other && (
          <Link to={`/profile/${other.username}`} className="flex items-center gap-12">
            <Avatar src={other.avatar_url} name={other.full_name} size={42} />
            <div><div className="bold">{other.full_name}</div><div className="small muted">@{other.username}</div></div>
          </Link>
        )}
      </div>
      <div className="chat-messages">
        {messages === null ? <Spinner /> : messages.length === 0 ? <p className="muted" style={{ margin: 'auto' }}>No messages yet — say hello 👋</p> : withTimes.map((m) => (
          <div key={m.id} style={{ display: 'contents' }}>
            {m.showTime && <div className="bubble-time">{new Date(m.created_at).toLocaleString(undefined, { weekday: 'short', hour: 'numeric', minute: '2-digit' })}</div>}
            <div className={`bubble ${m.sender_id === profile.id ? 'mine' : ''}`}>
              {m.body.split(/(https?:\/\/\S+)/g).map((part, i) => (/^https?:\/\//.test(part)
                ? <a key={i} href={part} style={{ textDecoration: 'underline' }}>{part.replace(window.location.origin, '')}</a>
                : part))}
            </div>
          </div>
        ))}
        <div ref={bottom} />
      </div>
      <form className="chat-compose" onSubmit={send}>
        <input placeholder="Aa" value={text} onChange={(e) => setText(e.target.value)} maxLength={4000} autoFocus />
        <button className="btn btn-primary" disabled={!text.trim()} aria-label="Send"><SendHorizontal size={18} /></button>
      </form>
    </>
  )
}

function NewChatModal({ onClose }) {
  const { profile } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [q, setQ] = useState('')
  const [people, setPeople] = useState([])
  useEffect(() => {
    const t = setTimeout(() => searchPeople(q.trim(), 12).then((p) => setPeople(p.filter((x) => x.id !== profile.id))).catch(() => {}), 250)
    return () => clearTimeout(t)
  }, [q, profile.id])

  const start = async (person) => {
    try { const id = await startConversation(person.id); onClose(); navigate(`/messages/${id}`) } catch (e) { toast(e.message, 'error') }
  }

  return (
    <Modal title="New message" onClose={onClose}>
      <div className="search mb-12" style={{ maxWidth: 'none' }}><Search size={16} color="var(--muted)" /><input autoFocus placeholder="Search neighbors by name" value={q} onChange={(e) => setQ(e.target.value)} /></div>
      <div className="mini-list">
        {people.map((p) => (
          <button key={p.id} className="mini-item" style={{ border: 0, background: 'none', cursor: 'pointer', textAlign: 'left' }} onClick={() => start(p)}>
            <Avatar src={p.avatar_url} name={p.full_name} size={40} />
            <div><div className="t">{p.full_name}</div><div className="s">@{p.username}{p.neighborhood?.name ? ` · ${p.neighborhood.name}` : ''}</div></div>
          </button>
        ))}
        {people.length === 0 && <p className="muted small">No neighbors found.</p>}
      </div>
    </Modal>
  )
}

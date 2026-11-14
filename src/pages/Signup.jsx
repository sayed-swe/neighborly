import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { supabase } from '../lib/supabase'
import { getNeighborhoods } from '../lib/api'
import { ErrorText, Field } from '../components/ui'
import AuthHero from './AuthHero'

export default function Signup() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ full_name: '', username: '', email: '', phone: '', password: '', neighborhood_id: '' })
  const [neighborhoods, setNeighborhoods] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  useEffect(() => { getNeighborhoods().then(setNeighborhoods).catch(() => {}) }, [])
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    const username = form.username.trim().toLowerCase()
    if (!/^[a-z0-9_.]{3,30}$/.test(username)) return setError('Username: 3–30 characters, letters, numbers, dot or underscore.')
    if (form.password.length < 8) return setError('Password must be at least 8 characters.')
    setBusy(true)
    const { data, error } = await supabase.auth.signUp({
      email: form.email.trim(),
      password: form.password,
      options: {
        emailRedirectTo: window.location.origin,
        data: {
          full_name: form.full_name.trim(),
          username,
          phone: form.phone.trim(),
          neighborhood_id: form.neighborhood_id || null,
        },
      },
    })
    setBusy(false)
    if (error) return setError(error.message)
    if (data.session) navigate('/', { replace: true })
    else setDone(true) // email confirmation is switched on in Supabase
  }

  return (
    <div className="auth-wrap">
      <AuthHero />
      <div className="auth-form-wrap">
        {done ? (
          <div className="auth-card">
            <h2>Check your email</h2>
            <p className="sub">We sent a confirmation link to <b>{form.email}</b>. Click it, then log in.</p>
            <Link to="/login" className="btn btn-primary btn-block">Go to log in</Link>
          </div>
        ) : (
          <form className="auth-card" onSubmit={submit}>
            <h2>Join your neighborhood</h2>
            <p className="sub">It takes less than a minute.</p>
            <ErrorText error={error} />
            <Field label="Full name"><input className="input" required maxLength={100} value={form.full_name} onChange={set('full_name')} /></Field>
            <div className="row">
              <Field label="Username"><input className="input" required value={form.username} onChange={set('username')} placeholder="e.g. samin" /></Field>
              <Field label="Phone (optional)"><input className="input" value={form.phone} onChange={set('phone')} maxLength={20} /></Field>
            </div>
            <Field label="Your neighborhood">
              <select className="select" value={form.neighborhood_id} onChange={set('neighborhood_id')} required>
                <option value="">Choose where you live…</option>
                {neighborhoods.map((n) => <option key={n.id} value={n.id}>{n.name}{n.city ? `, ${n.city}` : ''}</option>)}
              </select>
            </Field>
            <Field label="Email"><input className="input" type="email" autoComplete="email" required value={form.email} onChange={set('email')} /></Field>
            <Field label="Password" hint="At least 8 characters">
              <input className="input" type="password" autoComplete="new-password" required value={form.password} onChange={set('password')} />
            </Field>
            <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
            <div className="auth-switch">Already a member? <Link to="/login">Log in</Link></div>
          </form>
        )}
      </div>
    </div>
  )
}

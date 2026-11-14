import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { supabase } from '../lib/supabase'
import { ErrorText, Field } from '../components/ui'
import AuthHero from './AuthHero'

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password })
    setBusy(false)
    if (error) return setError(error.message)
    navigate(location.state?.from?.pathname || '/', { replace: true })
  }

  const forgot = async () => {
    if (!email.trim()) return setError('Enter your email first, then click "Forgot password".')
    const { error } = await supabase.auth.resetPasswordForEmail(email.trim(), { redirectTo: window.location.origin })
    if (error) setError(error.message)
    else setInfo('Check your inbox for a password reset link.')
  }

  return (
    <div className="auth-wrap">
      <AuthHero />
      <div className="auth-form-wrap">
        <form className="auth-card" onSubmit={submit}>
          <h2>Welcome back</h2>
          <p className="sub">Log in to see what's happening around you.</p>
          <ErrorText error={error} />
          {info && <div className="pill green mb-12" style={{ padding: '8px 12px' }}>{info}</div>}
          <Field label="Email">
            <input className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Password">
            <input className="input" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
          <div className="auth-switch"><button type="button" className="link" onClick={forgot}>Forgot password?</button></div>
          <div className="auth-switch">New to Neighborly? <Link to="/signup">Create an account</Link></div>
        </form>
      </div>
    </div>
  )
}

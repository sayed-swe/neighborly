import { useState } from 'react'
import { createReport } from '../lib/api'
import { REPORT_REASONS } from '../lib/constants'
import { labelize } from '../lib/format'
import { useToast } from '../context/ToastContext'
import { ErrorText, Field, Modal } from './ui'

export default function ReportModal({ targetType, targetId, onClose }) {
  const toast = useToast()
  const [reason, setReason] = useState('spam')
  const [details, setDetails] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async () => {
    setBusy(true); setError('')
    try {
      await createReport(targetType, targetId, reason, details.trim() || null)
      toast('Thanks — our moderators will review this.')
      onClose()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <Modal title="Report" onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-danger" onClick={submit} disabled={busy}>Submit report</button></>}>
      <ErrorText error={error} />
      <p className="muted mb-16">Tell us what's wrong. Reports are private — the person won't know who reported them.</p>
      <Field label="Reason">
        <div className="chips">
          {REPORT_REASONS.map((r) => (
            <button key={r} type="button" className={`chip ${reason === r ? 'active' : ''}`} onClick={() => setReason(r)}>{labelize(r)}</button>
          ))}
        </div>
      </Field>
      <Field label="Details (optional)">
        <textarea className="textarea" value={details} onChange={(e) => setDetails(e.target.value)} maxLength={2000} />
      </Field>
    </Modal>
  )
}

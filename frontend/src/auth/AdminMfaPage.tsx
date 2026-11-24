import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, ApiError } from '../api/client'
import { useMe } from './useMe'

type Enrollment = { manualKey: string; otpauthUri: string }
type EnrollmentComplete = { user: { role: 'ADMIN' }; recoveryCodes: string[] }

/** EEC-75: no admin session exists until TOTP/recovery succeeds. */
export function AdminMfaPage() {
  const { refresh } = useMe()
  const navigate = useNavigate()
  const [enrollment, setEnrollment] = useState<Enrollment>()
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>()
  const [code, setCode] = useState('')
  const [useRecovery, setUseRecovery] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function begin() {
    setError('')
    setBusy(true)
    try {
      setEnrollment(
        await apiFetch<Enrollment>('/api/public/auth/mfa/enroll/start', { method: 'POST' }),
      )
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Unable to start setup. Sign in again.')
    } finally {
      setBusy(false)
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (enrollment) {
        const result = await apiFetch<EnrollmentComplete>('/api/public/auth/mfa/enroll/confirm', {
          method: 'POST',
          body: { code },
        })
        setRecoveryCodes(result.recoveryCodes)
        await refresh()
      } else {
        await apiFetch('/api/public/auth/mfa/' + (useRecovery ? 'recovery' : 'verify'), {
          method: 'POST',
          body: useRecovery ? { recoveryCode: code } : { code },
        })
        await refresh()
        navigate('/admin', { replace: true })
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Verification failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="fl-auth-shell">
      <section className="fl-card fl-auth-card">
        <h1>Admin two-step verification</h1>
        {error && (
          <div className="fl-alert fl-alert--danger" role="alert">
            {error}
          </div>
        )}
        {recoveryCodes ? (
          <>
            <h2>Save your recovery codes now</h2>
            <p>Each code works once. Store these somewhere secure. They will not be shown again.</p>
            <pre aria-label="Recovery codes">{recoveryCodes.join('\n')}</pre>
            <button
              type="button"
              className="fl-btn fl-btn--primary"
              onClick={() => navigate('/admin', { replace: true })}
            >
              I have saved my codes
            </button>
          </>
        ) : (
          <>
            {!enrollment && (
              <p>
                Enter a code from your authenticator app, or set it up if this is your first admin
                login.
              </p>
            )}
            {enrollment && (
              <>
                <p>
                  Add an account manually in your authenticator app using the key below, then enter
                  its 6-digit code.
                </p>
                <p>
                  <strong>Setup key:</strong> <code>{enrollment.manualKey}</code>
                </p>
                <p>
                  <small>Use time-based (TOTP), SHA-1, 6 digits, 30 seconds.</small>
                </p>
              </>
            )}
            <form onSubmit={submit}>
              <label htmlFor="admin-mfa-code">
                {useRecovery && !enrollment ? 'Recovery code' : 'Authenticator code'}
              </label>
              <input
                className="fl-input"
                id="admin-mfa-code"
                value={code}
                autoComplete="one-time-code"
                required
                onChange={(event) => setCode(event.target.value)}
              />
              <button className="fl-btn fl-btn--primary fl-btn--block" disabled={busy}>
                {busy ? 'Verifying…' : enrollment ? 'Confirm authenticator' : 'Verify and log in'}
              </button>
            </form>
            {!enrollment && (
              <div className="fl-admin-mfa__actions">
                <button
                  type="button"
                  className="fl-btn fl-admin-mfa__secondary"
                  onClick={() => {
                    setUseRecovery(!useRecovery)
                    setCode('')
                  }}
                >
                  {useRecovery ? 'Use authenticator code' : 'Use a recovery code'}
                </button>
                <button
                  type="button"
                  className="fl-btn fl-admin-mfa__secondary"
                  disabled={busy}
                  onClick={() => void begin()}
                >
                  Set up authenticator (first login)
                </button>
              </div>
            )}
          </>
        )}
        <p>
          <Link to="/login">Back to login</Link>
        </p>
      </section>
    </main>
  )
}

import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, saveProvider } from '../api/client'
import { AlertIcon, EyeIcon, EyeOffIcon } from '../components/icons'
import { useAuth } from '../context/AuthContext'
import { useSavedProviders } from '../context/SavedProvidersContext'

const GENERIC_ERROR = 'Something went wrong. Please try again.'
const MIN_PASSWORD_LENGTH = 8

function RegisterPage() {
  const { register } = useAuth()
  const { refresh: refreshSavedProviders } = useSavedProviders()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }

    setIsSubmitting(true)
    try {
      await register(email, password, displayName)

      const pendingSaveProviderId = searchParams.get('saveProvider')
      if (pendingSaveProviderId) {
        try {
          await saveProvider(Number(pendingSaveProviderId))
          await refreshSavedProviders()
        } catch {
          // Account creation succeeded even if the pending save didn't go through.
        }
      }

      const redirect = searchParams.get('redirect')
      navigate(redirect && redirect.startsWith('/') ? redirect : '/')
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : GENERIC_ERROR)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-side">
        <Link className="wordmark" to="/">
          <span className="wordmark-mark" aria-hidden="true">
            +
          </span>
          DocFit <span className="wordmark-accent">AI</span>
        </Link>
        <h1 className="auth-side-heading">Create a free account to save your search.</h1>
        <p className="auth-side-copy">
          Keep a shortlist of providers you're considering and pick up past searches where you left off.
          We only save what you explicitly choose to save.
        </p>
      </div>

      <div className="auth-form-col">
        <div className="auth-card">
          <h2>Create your account</h2>
          <p className="auth-card-subtext">
            Already have an account?{' '}
            <Link to={`/signin${searchParams.toString() ? `?${searchParams.toString()}` : ''}`}>Sign in</Link>
          </p>

          {error && (
            <div className="state-panel error-panel auth-error" role="alert">
              <AlertIcon width={20} height={20} />
              <p>{error}</p>
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            <div className="field">
              <label htmlFor="register-name">Name (optional)</label>
              <input
                id="register-name"
                type="text"
                autoComplete="name"
                maxLength={100}
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="register-email">Email</label>
              <input
                id="register-email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="register-password">Password</label>
              <div className="password-field">
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  required
                  minLength={MIN_PASSWORD_LENGTH}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((show) => !show)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOffIcon width={18} height={18} /> : <EyeIcon width={18} height={18} />}
                </button>
              </div>
              <p className={`field-hint${passwordTooShort ? ' field-hint-error' : ''}`}>
                At least {MIN_PASSWORD_LENGTH} characters.
              </p>
            </div>

            <button type="submit" className="primary-button auth-submit" disabled={isSubmitting}>
              {isSubmitting && <span className="spinner" aria-hidden="true" />}
              Create account
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

export default RegisterPage

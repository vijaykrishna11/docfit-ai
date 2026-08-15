import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, saveProvider } from '../api/client'
import { AlertIcon, EyeIcon, EyeOffIcon } from '../components/icons'
import { useAuth } from '../context/AuthContext'
import { useSavedProviders } from '../context/SavedProvidersContext'

const GENERIC_ERROR = 'Something went wrong. Please try again.'

function SignInPage() {
  const { login } = useAuth()
  const { refresh: refreshSavedProviders } = useSavedProviders()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await login(email, password)

      const pendingSaveProviderId = searchParams.get('saveProvider')
      if (pendingSaveProviderId) {
        try {
          await saveProvider(Number(pendingSaveProviderId))
          await refreshSavedProviders()
        } catch {
          // Sign-in succeeded even if the pending save didn't go through -- don't block navigation.
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
        <h1 className="auth-side-heading">Find care that fits, and keep it with you.</h1>
        <p className="auth-side-copy">
          Sign in to save providers you trust and revisit past searches whenever you need them. Searching
          DocFit AI has never required an account, and it never will.
        </p>
      </div>

      <div className="auth-form-col">
        <div className="auth-card">
          <h2>Sign in</h2>
          <p className="auth-card-subtext">
            New to DocFit AI?{' '}
            <Link to={`/register${searchParams.toString() ? `?${searchParams.toString()}` : ''}`}>
              Create an account
            </Link>
          </p>

          {error && (
            <div className="state-panel error-panel auth-error" role="alert">
              <AlertIcon width={20} height={20} />
              <p>{error}</p>
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            <div className="field">
              <label htmlFor="signin-email">Email</label>
              <input
                id="signin-email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="signin-password">Password</label>
              <div className="password-field">
                <input
                  id="signin-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  minLength={8}
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
            </div>

            <button type="submit" className="primary-button auth-submit" disabled={isSubmitting}>
              {isSubmitting && <span className="spinner" aria-hidden="true" />}
              Sign in
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

export default SignInPage

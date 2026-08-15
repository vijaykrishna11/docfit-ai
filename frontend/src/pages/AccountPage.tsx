import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, deleteAccount, updateDisplayName } from '../api/client'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { AlertIcon, CheckIcon, UserIcon } from '../components/icons'
import { useAuth } from '../context/AuthContext'

const GENERIC_ERROR = 'Something went wrong. Please try again.'

function AccountPage() {
  const { user, logout, setUser } = useAuth()
  const navigate = useNavigate()

  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  if (!user) {
    return null
  }

  async function handleSaveName(event: FormEvent) {
    event.preventDefault()
    setSaveError(null)
    setSaveSuccess(false)
    setIsSaving(true)
    try {
      const updated = await updateDisplayName(displayName)
      setUser(updated)
      setSaveSuccess(true)
      window.setTimeout(() => setSaveSuccess(false), 2500)
    } catch (error) {
      setSaveError(error instanceof ApiError ? error.message : GENERIC_ERROR)
    } finally {
      setIsSaving(false)
    }
  }

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  async function handleDeleteAccount() {
    setDeleteError(null)
    setIsDeleting(true)
    try {
      await deleteAccount()
      navigate('/')
    } catch (error) {
      setDeleteError(error instanceof ApiError ? error.message : GENERIC_ERROR)
      setIsDeleting(false)
    }
  }

  return (
    <div className="page">
      <Header />
      <main className="container detail-content account-content">
        <div>
          <h1>Account</h1>
          <p className="results-subtext">Manage your DocFit AI account.</p>
        </div>

        <section className="account-card">
          <div className="account-summary">
            <div className="avatar avatar-lg" aria-hidden="true">
              <UserIcon width={26} height={26} />
            </div>
            <div>
              <h2>{user.displayName || 'DocFit AI member'}</h2>
              <p className="results-subtext">{user.email}</p>
              <p className="results-subtext">Member since {new Date(user.createdAt).toLocaleDateString()}</p>
            </div>
          </div>

          <form className="auth-form account-name-form" onSubmit={handleSaveName}>
            <div className="field">
              <label htmlFor="account-display-name">Display name</label>
              <input
                id="account-display-name"
                type="text"
                maxLength={100}
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="Not set"
              />
            </div>
            {saveError && (
              <div className="state-panel error-panel auth-error" role="alert">
                <AlertIcon width={18} height={18} />
                <p>{saveError}</p>
              </div>
            )}
            <button type="submit" className="secondary-button" disabled={isSaving}>
              {isSaving && <span className="spinner spinner-sm" aria-hidden="true" />}
              {saveSuccess && !isSaving && <CheckIcon width={14} height={14} />}
              Save changes
            </button>
          </form>

          <button type="button" className="secondary-button" onClick={handleLogout}>
            Sign out
          </button>
        </section>

        <section className="account-card account-danger-zone">
          <h2>Delete account</h2>
          <p className="results-subtext">
            Permanently deletes your account along with your saved providers and saved searches. This
            cannot be undone.
          </p>
          {deleteError && (
            <div className="state-panel error-panel auth-error" role="alert">
              <AlertIcon width={18} height={18} />
              <p>{deleteError}</p>
            </div>
          )}
          {!confirmingDelete ? (
            <button type="button" className="secondary-button danger-button" onClick={() => setConfirmingDelete(true)}>
              Delete my account
            </button>
          ) : (
            <div className="delete-confirm-row">
              <p>Are you sure? This will permanently delete your account and all saved data.</p>
              <div className="delete-confirm-actions">
                <button type="button" className="secondary-button" onClick={() => setConfirmingDelete(false)}>
                  Cancel
                </button>
                <button
                  type="button"
                  className="primary-button danger-button-solid"
                  onClick={handleDeleteAccount}
                  disabled={isDeleting}
                >
                  {isDeleting && <span className="spinner" aria-hidden="true" />}
                  Yes, delete my account
                </button>
              </div>
            </div>
          )}
        </section>
      </main>
      <Footer />
    </div>
  )
}

export default AccountPage

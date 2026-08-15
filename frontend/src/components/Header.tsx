import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { BookmarkIcon, LogOutIcon, UserIcon } from './icons'

const NAV_LINKS = [
  { href: '#search-panel', label: 'Find Care' },
  { href: '#how-it-works', label: 'How It Works' },
  { href: '#data-sources', label: 'Data Sources' },
  { href: '#about', label: 'About' },
]

function Header() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { user, isAuthenticated, isLoading, logout } = useAuth()

  useEffect(() => {
    if (!menuOpen) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [menuOpen])

  function closeMenu() {
    setMenuOpen(false)
  }

  return (
    <header className="site-header">
      <div className="container site-header-inner">
        <Link className="wordmark" to="/#top">
          <span className="wordmark-mark" aria-hidden="true">
            +
          </span>
          DocFit <span className="wordmark-accent">AI</span>
        </Link>

        <nav className="site-nav" aria-label="Primary">
          {NAV_LINKS.map((link) => (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ))}
          <a href="https://github.com/vijaykrishna11/docfit-ai" target="_blank" rel="noopener noreferrer">
            GitHub
          </a>
        </nav>

        <div className="site-header-account">
          {!isLoading && (isAuthenticated && user ? <AccountMenu displayName={user.displayName || user.email} onSignOut={logout} /> : <AuthLinks />)}
        </div>

        <button
          type="button"
          className="mobile-menu-toggle"
          aria-expanded={menuOpen}
          aria-controls="mobile-nav"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className="visually-hidden">{menuOpen ? 'Close menu' : 'Open menu'}</span>
          <span className="menu-bar" aria-hidden="true" />
          <span className="menu-bar" aria-hidden="true" />
          <span className="menu-bar" aria-hidden="true" />
        </button>
      </div>

      {menuOpen && (
        <nav id="mobile-nav" className="mobile-nav" aria-label="Mobile">
          {NAV_LINKS.map((link) => (
            <a key={link.href} href={link.href} onClick={closeMenu}>
              {link.label}
            </a>
          ))}
          <a
            href="https://github.com/vijaykrishna11/docfit-ai"
            target="_blank"
            rel="noopener noreferrer"
            onClick={closeMenu}
          >
            GitHub
          </a>
          {!isLoading &&
            (isAuthenticated ? (
              <>
                <Link to="/saved" onClick={closeMenu}>
                  Saved providers
                </Link>
                <Link to="/saved-searches" onClick={closeMenu}>
                  Saved searches
                </Link>
                <Link to="/account" onClick={closeMenu}>
                  Account
                </Link>
                <a
                  href="#top"
                  onClick={(event) => {
                    event.preventDefault()
                    closeMenu()
                    void logout()
                  }}
                >
                  Sign out
                </a>
              </>
            ) : (
              <>
                <Link to="/signin" onClick={closeMenu}>
                  Sign in
                </Link>
                <Link to="/register" onClick={closeMenu}>
                  Create account
                </Link>
              </>
            ))}
        </nav>
      )}
    </header>
  )
}

function AuthLinks() {
  return (
    <div className="auth-nav-links">
      <Link className="secondary-button" to="/signin">
        Sign in
      </Link>
      <Link className="primary-button" to="/register">
        Create account
      </Link>
    </div>
  )
}

function AccountMenu({ displayName, onSignOut }: { displayName: string; onSignOut: () => Promise<void> }) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- close the dropdown on navigation
    setOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!open) return
    function handleClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClick)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  const initials = displayName
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <div className="account-menu" ref={containerRef}>
      <button
        type="button"
        className="account-menu-trigger"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <span className="avatar account-menu-avatar" aria-hidden="true">
          {initials || <UserIcon width={16} height={16} />}
        </span>
      </button>

      {open && (
        <div className="account-menu-dropdown" role="menu">
          <p className="account-menu-name">{displayName}</p>
          <Link to="/saved" role="menuitem" onClick={() => setOpen(false)}>
            <BookmarkIcon width={15} height={15} />
            Saved providers
          </Link>
          <Link to="/saved-searches" role="menuitem" onClick={() => setOpen(false)}>
            <BookmarkIcon width={15} height={15} />
            Saved searches
          </Link>
          <Link to="/account" role="menuitem" onClick={() => setOpen(false)}>
            <UserIcon width={15} height={15} />
            Account
          </Link>
          <button
            type="button"
            role="menuitem"
            className="account-menu-signout"
            onClick={async () => {
              setOpen(false)
              await onSignOut()
              navigate('/')
            }}
          >
            <LogOutIcon width={15} height={15} />
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}

export default Header

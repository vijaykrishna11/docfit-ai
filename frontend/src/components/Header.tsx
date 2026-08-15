import { useEffect, useState } from 'react'

const NAV_LINKS = [
  { href: '#search-panel', label: 'Find Care' },
  { href: '#how-it-works', label: 'How It Works' },
  { href: '#data-sources', label: 'Data Sources' },
  { href: '#about', label: 'About' },
]

function Header() {
  const [menuOpen, setMenuOpen] = useState(false)

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
        <a className="wordmark" href="#top">
          <span className="wordmark-mark" aria-hidden="true">
            +
          </span>
          DocFit <span className="wordmark-accent">AI</span>
        </a>

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
        </nav>
      )}
    </header>
  )
}

export default Header

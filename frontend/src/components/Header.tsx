function Header() {
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
          <a href="#search-panel">Find care</a>
          <a href="https://github.com/vijaykrishna11/docfit-ai" target="_blank" rel="noopener noreferrer">
            GitHub
          </a>
        </nav>
      </div>
    </header>
  )
}

export default Header

function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer-inner">
        <div className="site-footer-brand">
          <p className="footer-wordmark">DocFit AI</p>
          <p className="footer-tagline">Healthcare navigation, not medical advice.</p>
        </div>

        <nav className="footer-links" aria-label="Footer">
          <a href="#search-panel">Find Care</a>
          <a href="#how-it-works">How It Works</a>
          <a href="#data-sources">Data Sources</a>
          <a href="#about">About</a>
          <a href="https://github.com/vijaykrishna11/docfit-ai" target="_blank" rel="noopener noreferrer">
            GitHub
          </a>
        </nav>

        <p className="footer-data-note">Provider information sourced from public NPPES/NPI data.</p>
      </div>
    </footer>
  )
}

export default Footer

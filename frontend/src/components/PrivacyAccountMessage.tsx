import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { HeartIcon } from './icons'

function PrivacyAccountMessage() {
  const { isAuthenticated } = useAuth()

  if (isAuthenticated) {
    return null
  }

  return (
    <section className="page-section page-section-muted" aria-labelledby="privacy-account-heading">
      <div className="container privacy-account-content">
        <span className="privacy-account-icon" aria-hidden="true">
          <HeartIcon width={22} height={22} />
        </span>
        <h2 id="privacy-account-heading">Search freely. Save what matters.</h2>
        <p>You don&rsquo;t need an account to search for care -- every search on this page works anonymously.</p>
        <p>Create a free account only when you want to:</p>
        <ul className="privacy-account-list">
          <li>Save providers you&rsquo;re considering</li>
          <li>Save a search to run again later</li>
          <li>Keep a shortlist across visits</li>
        </ul>
        <Link className="secondary-button" to="/register">
          Create a free account
        </Link>
      </div>
    </section>
  )
}

export default PrivacyAccountMessage

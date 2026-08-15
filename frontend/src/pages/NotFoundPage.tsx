import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'

function NotFoundPage() {
  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <div className="state-panel empty-panel">
          <h1>Page not found</h1>
          <p>The page you&rsquo;re looking for doesn&rsquo;t exist or may have moved.</p>
          <Link className="primary-button" to="/">
            Back to DocFit AI
          </Link>
        </div>
      </main>
      <Footer />
    </div>
  )
}

export default NotFoundPage

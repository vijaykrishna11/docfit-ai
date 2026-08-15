import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="page">
          <main className="container detail-content">
            <div className="state-panel error-panel">
              <div className="state-panel-copy">
                <h3>Something went wrong</h3>
                <p>Please refresh the page. If the problem continues, try again later.</p>
              </div>
              <button type="button" className="secondary-button" onClick={() => window.location.reload()}>
                Refresh
              </button>
            </div>
          </main>
        </div>
      )
    }
    return this.props.children
  }
}

export default ErrorBoundary

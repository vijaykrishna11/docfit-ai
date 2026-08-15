import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import ErrorBoundary from './components/ErrorBoundary'
import ProtectedRoute from './components/ProtectedRoute'
import { AuthProvider } from './context/AuthContext'
import { CompareProvider } from './context/CompareContext'
import { SavedProvidersProvider } from './context/SavedProvidersContext'
import AccountPage from './pages/AccountPage'
import ComparePage from './pages/ComparePage'
import HomePage from './pages/HomePage'
import NotFoundPage from './pages/NotFoundPage'
import ProviderDetailPage from './pages/ProviderDetailPage'
import RegisterPage from './pages/RegisterPage'
import SavedProvidersPage from './pages/SavedProvidersPage'
import SavedSearchesPage from './pages/SavedSearchesPage'
import SignInPage from './pages/SignInPage'

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <SavedProvidersProvider>
            <CompareProvider>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/providers/:id" element={<ProviderDetailPage />} />
                <Route path="/compare" element={<ComparePage />} />
                <Route path="/signin" element={<SignInPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route
                  path="/account"
                  element={
                    <ProtectedRoute>
                      <AccountPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/saved"
                  element={
                    <ProtectedRoute>
                      <SavedProvidersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/saved-searches"
                  element={
                    <ProtectedRoute>
                      <SavedSearchesPage />
                    </ProtectedRoute>
                  }
                />
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </CompareProvider>
          </SavedProvidersProvider>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  )
}

export default App

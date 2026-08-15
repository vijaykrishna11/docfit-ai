import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import { CompareProvider } from './context/CompareContext'
import ComparePage from './pages/ComparePage'
import HomePage from './pages/HomePage'
import ProviderDetailPage from './pages/ProviderDetailPage'

function App() {
  return (
    <BrowserRouter>
      <CompareProvider>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/providers/:id" element={<ProviderDetailPage />} />
          <Route path="/compare" element={<ComparePage />} />
        </Routes>
      </CompareProvider>
    </BrowserRouter>
  )
}

export default App

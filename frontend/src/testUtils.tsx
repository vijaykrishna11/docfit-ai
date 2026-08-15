import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { CompareProvider } from './context/CompareContext'
import { SavedProvidersProvider } from './context/SavedProvidersContext'

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string
}

export function renderWithProviders(ui: ReactElement, options?: RenderWithProvidersOptions) {
  const { route = '/', ...renderOptions } = options ?? {}
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <SavedProvidersProvider>
          <CompareProvider>{ui}</CompareProvider>
        </SavedProvidersProvider>
      </AuthProvider>
    </MemoryRouter>,
    renderOptions,
  )
}

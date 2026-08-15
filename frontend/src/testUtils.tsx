import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { CompareProvider } from './context/CompareContext'

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string
}

export function renderWithProviders(ui: ReactElement, options?: RenderWithProvidersOptions) {
  const { route = '/', ...renderOptions } = options ?? {}
  return render(
    <MemoryRouter initialEntries={[route]}>
      <CompareProvider>{ui}</CompareProvider>
    </MemoryRouter>,
    renderOptions,
  )
}

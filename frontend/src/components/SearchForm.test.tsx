import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import SearchForm from './SearchForm'

describe('SearchForm', () => {
  it('renders the specialty/insurance dropdowns, ZIP input, and search button', () => {
    render(
      <SearchForm
        specialties={[{ code: 'CARDIOLOGY', name: 'Cardiology' }]}
        insuranceCarriers={[{ id: 1, name: 'Aetna' }]}
        onSearch={vi.fn()}
      />,
    )

    expect(screen.getByLabelText(/specialty/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/insurance/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/zip code/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /search/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Cardiology' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Aetna' })).toBeInTheDocument()
  })
})

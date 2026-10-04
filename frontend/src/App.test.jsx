import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const sample = { code:'launch', shortUrl:'http://localhost:3000/r/launch', destinationUrl:'https://example.com/launch', createdAt:'2026-10-04T08:00:00Z', expiresAt:null, active:true, clicks:3 }

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok:true, status:200, json:async () => [] }))
  Object.assign(navigator, { clipboard: { writeText: vi.fn() } })
})
afterEach(() => cleanup())

describe('Snip dashboard', () => {
  it('renders an empty workspace after loading', async () => {
    render(<App />)
    expect(await screen.findByText('No links yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name:/shorten link/i })).toBeInTheDocument()
  })

  it('creates a short link and displays the result', async () => {
    fetch
      .mockResolvedValueOnce({ ok:true, status:200, json:async () => [] })
      .mockResolvedValueOnce({ ok:true, status:201, json:async () => sample })
      .mockResolvedValueOnce({ ok:true, status:200, json:async () => [sample] })
    render(<App />)
    await screen.findByText('No links yet')
    await userEvent.type(screen.getByLabelText('Paste a long URL'), 'https://example.com/launch')
    await userEvent.click(screen.getByRole('button', { name:/shorten link/i }))
    expect(await screen.findByText('Your short link is ready')).toBeInTheDocument()
    expect(screen.getAllByText(/localhost:3000\/r\/launch/).length).toBeGreaterThan(0)
  })

  it('shows server validation errors', async () => {
    fetch
      .mockResolvedValueOnce({ ok:true, status:200, json:async () => [] })
      .mockResolvedValueOnce({ ok:false, status:409, json:async () => ({ message:'That custom alias is already in use.' }) })
    render(<App />)
    await screen.findByText('No links yet')
    await userEvent.type(screen.getByLabelText('Paste a long URL'), 'https://example.com')
    await userEvent.click(screen.getByRole('button', { name:/shorten link/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already in use')
  })

  it('refreshes link stats without reloading the page', async () => {
    const updated = { ...sample, clicks: 8 }
    fetch
      .mockResolvedValueOnce({ ok:true, status:200, json:async () => [sample] })
      .mockResolvedValueOnce({ ok:true, status:200, json:async () => [updated] })
    render(<App />)
    await screen.findByText('Your links')
    expect(screen.getAllByText('3')).toHaveLength(2)

    await userEvent.click(screen.getByRole('button', { name:'Refresh latest stats' }))

    await waitFor(() => expect(screen.getAllByText('8')).toHaveLength(2))
    expect(screen.getByText(/Stats refreshed/)).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledTimes(2)
  })
})

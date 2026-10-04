import { useEffect, useMemo, useState } from 'react'
import { api } from './api'

const Icon = ({ name, size = 18 }) => {
  const paths = {
    link: <><path d="M10 13a5 5 0 0 0 7.54.54l2-2a5 5 0 0 0-7.07-7.07l-1.15 1.15"/><path d="M14 11a5 5 0 0 0-7.54-.54l-2 2a5 5 0 0 0 7.07 7.07l1.14-1.14"/></>,
    copy: <><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></>,
    chart: <><path d="M3 3v18h18"/><path d="m7 16 4-5 4 3 5-7"/></>,
    edit: <><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></>,
    trash: <><path d="M3 6h18"/><path d="M8 6V4h8v2M19 6l-1 14H6L5 6"/></>,
    arrow: <><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></>,
    spark: <><path d="m12 3-1.7 4.3L6 9l4.3 1.7L12 15l1.7-4.3L18 9l-4.3-1.7Z"/><path d="m5 17-.8 2.2L2 20l2.2.8L5 23l.8-2.2L8 20l-2.2-.8Z"/></>,
    refresh: <><path d="M20 6v5h-5"/><path d="M19 11a7.5 7.5 0 1 0 .2 5"/></>,
    close: <><path d="m6 6 12 12M18 6 6 18"/></>,
  }
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}

const formatDate = (value) => value ? new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Never'
const host = (url) => { try { return new URL(url).hostname.replace(/^www\./, '') } catch { return url } }

export default function App() {
  const [links, setLinks] = useState([])
  const [form, setForm] = useState({ url: '', customAlias: '', expiresAt: '' })
  const [advanced, setAdvanced] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [toast, setToast] = useState('')
  const [analytics, setAnalytics] = useState(null)
  const [editing, setEditing] = useState(null)
  const [refreshing, setRefreshing] = useState(false)

  const refresh = () => api.list().then(setLinks).catch(() => setError('Could not load your recent links.'))
  useEffect(() => { refresh() }, [])
  useEffect(() => {
    const code = new URLSearchParams(window.location.search).get('analytics')
    if (code) api.analytics(code).then(setAnalytics).catch(() => {})
  }, [])
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(''), 2200); return () => clearTimeout(timer) }, [toast])

  const stats = useMemo(() => ({
    links: links.length,
    clicks: links.reduce((sum, item) => sum + item.clicks, 0),
    active: links.filter(item => item.active).length,
  }), [links])

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const payload = { url: form.url, customAlias: form.customAlias || null, expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null }
      const created = await api.create(payload)
      setResult(created); setForm({ url: '', customAlias: '', expiresAt: '' }); await refresh()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function copy(text) {
    await navigator.clipboard.writeText(text); setToast('Short link copied')
  }

  async function openAnalytics(code) {
    try { setAnalytics(await api.analytics(code)) } catch (err) { setToast(err.message) }
  }

  async function refreshStats() {
    setRefreshing(true)
    try {
      const [freshLinks, freshAnalytics] = await Promise.all([
        api.list(),
        analytics ? api.analytics(analytics.link.code) : Promise.resolve(null),
      ])
      setLinks(freshLinks)
      if (freshAnalytics) setAnalytics(freshAnalytics)
      setToast('Stats refreshed')
    } catch (err) {
      setToast(err.message || 'Could not refresh stats.')
    } finally {
      setRefreshing(false)
    }
  }

  async function remove(code) {
    if (!window.confirm(`Delete ${code} and its click history?`)) return
    try { await api.remove(code); setToast('Link deleted'); await refresh() } catch (err) { setToast(err.message) }
  }

  async function saveEdit(event) {
    event.preventDefault()
    try {
      await api.update(editing.code, { url: editing.destinationUrl, expiresAt: editing.expiresAt ? new Date(editing.expiresAt).toISOString() : null })
      setEditing(null); setToast('Link updated'); await refresh()
    } catch (err) { setToast(err.message) }
  }

  return <div className="app-shell">
    <nav className="nav wrap">
      <a className="brand" href="#top" aria-label="Snip home"><span className="brand-mark"><Icon name="link" size={19}/></span>snip<span>.</span></a>
      <div className="nav-links"><a href="#links">My links</a><a href="/swagger-ui/index.html" target="_blank">API docs</a><span className="status"><i/>All systems ready</span></div>
    </nav>

    <main id="top">
      <section className="hero wrap">
        <div className="eyebrow"><Icon name="spark" size={15}/> Smart links, zero clutter</div>
        <h1>Make every link<br/><em>work harder.</em></h1>
        <p className="hero-copy">Shorten, share, and understand your links from one focused workspace.</p>

        <form className="shorten-card" onSubmit={submit}>
          <label htmlFor="url">Paste a long URL</label>
          <div className="url-row">
            <span className="input-icon"><Icon name="link"/></span>
            <input id="url" type="url" placeholder="https://example.com/a-very-long-link" value={form.url} onChange={e => setForm({...form, url: e.target.value})} required/>
            <button className="primary" disabled={busy}>{busy ? 'Creating…' : 'Shorten link'} <Icon name="arrow"/></button>
          </div>
          <button type="button" className="advanced-toggle" onClick={() => setAdvanced(!advanced)}>{advanced ? '− Hide options' : '+ Custom alias & expiry'}</button>
          {advanced && <div className="advanced-fields">
            <div><label htmlFor="alias">Custom alias <small>optional</small></label><div className="prefixed"><span>snip/</span><input id="alias" maxLength="8" pattern="[A-Za-z0-9]+" placeholder="launch" value={form.customAlias} onChange={e => setForm({...form, customAlias: e.target.value})}/></div></div>
            <div><label htmlFor="expiry">Expires <small>optional</small></label><input id="expiry" type="datetime-local" value={form.expiresAt} onChange={e => setForm({...form, expiresAt: e.target.value})}/></div>
          </div>}
          {error && <p className="form-error" role="alert">{error}</p>}
        </form>

        {result && <div className="result-card" aria-live="polite">
          <div className="success-dot">✓</div><div><small>Your short link is ready</small><strong>{result.shortUrl}</strong></div>
          <button className="copy-button" onClick={() => copy(result.shortUrl)}><Icon name="copy"/> Copy</button>
          <button className="icon-button" aria-label="Close result" onClick={() => setResult(null)}><Icon name="close"/></button>
        </div>}
      </section>

      <section className="workspace" id="links">
        <div className="wrap">
          <div className="section-heading"><div><span className="kicker">Workspace</span><div className="workspace-title"><h2>Your links</h2><button className="refresh-stats" type="button" onClick={refreshStats} disabled={refreshing} aria-label="Refresh latest stats" title="Refresh latest stats"><Icon name="refresh" size={16}/><span>{refreshing ? 'Refreshing…' : 'Refresh stats'}</span></button></div><p>Recent links and the signals they collect.</p></div>
            <div className="summary"><span><b>{stats.links}</b> created</span><span><b>{stats.clicks}</b> clicks</span><span><b>{stats.active}</b> active</span></div>
          </div>
          <div className="table-card">
            {links.length === 0 ? <div className="empty"><span><Icon name="link" size={26}/></span><h3>No links yet</h3><p>Your first short link will appear here.</p></div> : <>
              <div className="table-head"><span>Short link</span><span>Destination</span><span>Created</span><span>Clicks</span><span>Actions</span></div>
              {links.map(item => <article className="link-row" key={item.code}>
                <div className="short-cell"><span className={`active-dot ${item.active ? '' : 'off'}`}/><div><a href={item.shortUrl} target="_blank">{item.shortUrl.replace(/^https?:\/\//, '')}</a><small>{item.active ? 'Active' : 'Expired'}</small></div><button className="mini-copy" aria-label={`Copy ${item.code}`} onClick={() => copy(item.shortUrl)}><Icon name="copy" size={15}/></button></div>
                <div className="destination"><span className="favicon">{host(item.destinationUrl).charAt(0).toUpperCase()}</span><div><b>{host(item.destinationUrl)}</b><small>{item.destinationUrl}</small></div></div>
                <time>{formatDate(item.createdAt)}</time>
                <button className="click-count" onClick={() => openAnalytics(item.code)}><Icon name="chart" size={16}/><b>{item.clicks}</b></button>
                <div className="actions"><button aria-label={`Edit ${item.code}`} onClick={() => setEditing(item)}><Icon name="edit"/></button><button aria-label={`Delete ${item.code}`} onClick={() => remove(item.code)}><Icon name="trash"/></button></div>
              </article>)}
            </>}
          </div>
        </div>
      </section>
    </main>

    <footer className="wrap"><a className="brand small" href="#top">snip<span>.</span></a><p>Short links. Clear signals. Built for focus.</p><span>Spring Boot · React · PostgreSQL · Redis</span></footer>
    {toast && <div className="toast">✓ {toast}</div>}

    {analytics && <div className="modal-backdrop" onMouseDown={e => e.target === e.currentTarget && setAnalytics(null)}><section className="modal analytics-modal">
      <button className="modal-close" aria-label="Close analytics" onClick={() => setAnalytics(null)}><Icon name="close"/></button>
      <span className="kicker">Link intelligence</span><h2>{analytics.link.code}</h2><p className="muted ellipsis">{analytics.link.destinationUrl}</p>
      <div className="metric-grid"><div><small>Total clicks</small><strong>{analytics.totalClicks}</strong></div><div><small>Unique visitors</small><strong>{analytics.uniqueVisitors}</strong></div><div><small>Top country</small><strong className="metric-text">{Object.keys(analytics.countries)[0] || '—'}</strong></div></div>
      <h3>Countries</h3><div className="country-list">{Object.entries(analytics.countries).length ? Object.entries(analytics.countries).map(([country, count]) => <div key={country}><span>{country}</span><span className="bar"><i style={{width: `${Math.max(8, count / analytics.totalClicks * 100)}%`}}/></span><b>{count}</b></div>) : <p className="muted">No visits recorded yet.</p>}</div>
      <h3>Recent clicks</h3><div className="recent-list">{analytics.recentClicks.length ? analytics.recentClicks.map((click, index) => <div key={index}><span>{click.country}</span><span>{click.device}</span><span>{click.referrer}</span><time>{formatDate(click.clickedAt)}</time></div>) : <p className="muted">Share this link to start collecting insights.</p>}</div>
    </section></div>}

    {editing && <div className="modal-backdrop"><form className="modal edit-modal" onSubmit={saveEdit}>
      <button type="button" className="modal-close" aria-label="Close editor" onClick={() => setEditing(null)}><Icon name="close"/></button>
      <span className="kicker">Edit destination</span><h2>{editing.code}</h2>
      <label htmlFor="edit-url">Destination URL</label><input id="edit-url" type="url" value={editing.destinationUrl} onChange={e => setEditing({...editing, destinationUrl: e.target.value})} required/>
      <label htmlFor="edit-expiry">Expiry <small>leave blank for no expiry</small></label><input id="edit-expiry" type="datetime-local" value={editing.expiresAt ? editing.expiresAt.slice(0, 16) : ''} onChange={e => setEditing({...editing, expiresAt: e.target.value})}/>
      <div className="modal-actions"><button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button><button className="primary">Save changes</button></div>
    </form></div>}
  </div>
}

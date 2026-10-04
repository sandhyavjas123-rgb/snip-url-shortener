async function request(path, options) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'The server could not complete the request.' }))
    throw new Error(error.message || 'Request failed')
  }
  return response.status === 204 ? null : response.json()
}

export const api = {
  list: () => request('/api/links'),
  create: (body) => request('/api/links', { method: 'POST', body: JSON.stringify(body) }),
  update: (code, body) => request(`/api/links/${code}`, { method: 'PUT', body: JSON.stringify(body) }),
  remove: (code) => request(`/api/links/${code}`, { method: 'DELETE' }),
  analytics: (code) => request(`/api/links/${code}/analytics`),
}


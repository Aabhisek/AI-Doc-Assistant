import axios from 'axios'

/**
 * Axios instance pointed at the Spring Boot API.
 * In development (Vite dev server), the vite.config.js proxy routes /api → localhost:8080.
 * In production (Docker), nginx proxies /api → the backend container.
 */
const api = axios.create({
  baseURL: '/api',
  timeout: 120_000, // 2 minutes — Groq is fast but large documents may take longer
})

// ── Documents ────────────────────────────────────────────────────────────────

export const documentApi = {
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api.post('/documents/upload', form)
  },
  list: () => api.get('/documents'),
  getById: (id) => api.get(`/documents/${id}`),
  delete: (id) => api.delete(`/documents/${id}`),
}

// ── Chat (RAG Q&A) ────────────────────────────────────────────────────────────

export const chatApi = {
  ask: (documentId, question) =>
    api.post('/chat', { documentId, question }),
}

// ── Analysis actions ──────────────────────────────────────────────────────────

export const summaryApi = {
  summarize: (documentId) => api.post(`/summary/${documentId}`),
}

export const extractionApi = {
  extract: (documentId, category) =>
    api.post('/extract', { documentId, category }),
}

export const comparisonApi = {
  compare: (documentIdA, documentIdB) =>
    api.post('/compare', { documentIdA, documentIdB }),
}

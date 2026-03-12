import { useState, useEffect, useCallback, useRef } from 'react'
import { FileText, MessageSquare, Sparkles, BookOpen } from 'lucide-react'
import DocumentUpload from './components/DocumentUpload'
import DocumentList from './components/DocumentList'
import ChatInput from './components/ChatInput'
import ChatMessage from './components/ChatMessage'
import ActionBar from './components/ActionBar'
import ResultPanel from './components/ResultPanel'
import { documentApi } from './services/api'

export default function App() {
  const [documents, setDocuments] = useState([])
  const [selectedDocId, setSelectedDocId] = useState(null)
  const [feed, setFeed] = useState([])   // unified timeline of chat + analysis
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const feedEndRef = useRef(null)

  const fetchDocuments = useCallback(async () => {
    try {
      const { data } = await documentApi.list()
      setDocuments(data)
    } catch {
      // backend not running yet — ignore silently
    }
  }, [])

  useEffect(() => {
    fetchDocuments()
  }, [fetchDocuments])

  // Scroll to bottom whenever the feed grows
  useEffect(() => {
    feedEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [feed, loading])

  const selectedDoc = documents.find((d) => d.id === selectedDocId) ?? null

  const addToFeed = (item) =>
    setFeed((prev) => [...prev, { ...item, id: Date.now() + Math.random() }])

  const handleDocumentUploaded = (doc) => {
    setDocuments((prev) => [doc, ...prev])
    setSelectedDocId(doc.id)
    setFeed([])
    setError(null)
  }

  const handleDocumentSelected = (docId) => {
    setSelectedDocId(docId)
    setFeed([])
    setError(null)
  }

  const handleDocumentDeleted = async (docId) => {
    try {
      await documentApi.delete(docId)
      setDocuments((prev) => prev.filter((d) => d.id !== docId))
      if (selectedDocId === docId) {
        setSelectedDocId(null)
        setFeed([])
      }
    } catch {
      setError('Failed to delete document')
    }
  }

  const handleChatMessage = async (question, sendFn) => {
    if (!selectedDocId) return
    addToFeed({ type: 'user', content: question })
    setLoading(true)
    setError(null)
    try {
      const { data } = await sendFn(selectedDocId, question)
      addToFeed({ type: 'assistant', content: data.answer, citations: data.citations })
    } catch (err) {
      setError(err.response?.data?.error ?? 'Failed to get answer')
    } finally {
      setLoading(false)
    }
  }

  // Analysis actions (summary / extraction / comparison) land in the same feed
  const handleActionResult = (result) => {
    addToFeed({ ...result, timestamp: new Date() })
  }

  return (
    <div className="flex h-screen overflow-hidden bg-gray-950">

      {/* ── Sidebar ── */}
      <aside className="flex w-80 shrink-0 flex-col border-r border-gray-800 bg-gray-900">

        {/* Brand */}
        <div className="flex items-center gap-2.5 border-b border-gray-800 px-5 py-4">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600">
            <BookOpen size={16} className="text-white" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-white">AI Doc Assistant</h1>
            <p className="text-xs text-gray-500">RAG · Groq · pgvector</p>
          </div>
        </div>

        {/* Upload */}
        <div className="border-b border-gray-800 p-4">
          <DocumentUpload onUploaded={handleDocumentUploaded} />
        </div>

        {/* Document list */}
        <div className="flex-1 overflow-y-auto">
          <DocumentList
            documents={documents}
            selectedDocId={selectedDocId}
            onSelect={handleDocumentSelected}
            onDelete={handleDocumentDeleted}
          />
        </div>

        {/* Analysis actions */}
        {selectedDoc?.status === 'READY' && (
          <div className="border-t border-gray-800 p-4">
            <ActionBar
              documents={documents.filter((d) => d.status === 'READY')}
              selectedDocId={selectedDocId}
              onResult={handleActionResult}
              setLoading={setLoading}
              setError={setError}
            />
          </div>
        )}
      </aside>

      {/* ── Main area ── */}
      <main className="flex flex-1 flex-col overflow-hidden">
        {selectedDoc ? (
          <>
            {/* Document header */}
            <div className="flex items-center gap-3 border-b border-gray-800 bg-gray-900 px-6 py-3">
              <FileText size={16} className="text-indigo-400 shrink-0" />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-white">{selectedDoc.name}</p>
                <p className="text-xs text-gray-500">
                  {selectedDoc.chunkCount} chunks · {(selectedDoc.fileSizeBytes / 1024).toFixed(1)} KB
                </p>
              </div>
              {feed.length > 0 && (
                <button
                  onClick={() => { setFeed([]); setError(null) }}
                  className="ml-auto shrink-0 text-xs text-gray-600 transition-colors hover:text-gray-300"
                >
                  Clear
                </button>
              )}
            </div>

            {/* ── Unified feed ── */}
            <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">

              {/* Empty placeholder */}
              {feed.length === 0 && !loading && (
                <EmptyFeedState docReady={selectedDoc.status === 'READY'} />
              )}

              {/* Feed items */}
              {feed.map((item) => (
                <FeedItem key={item.id} item={item} />
              ))}

              {/* Thinking indicator */}
              {loading && <LoadingDots />}

              {/* Error banner */}
              {error && (
                <div className="rounded-xl border border-red-800 bg-red-900/20 px-4 py-3 text-sm text-red-300">
                  {error}
                </div>
              )}

              {/* Scroll anchor */}
              <div ref={feedEndRef} />
            </div>

            {/* ── Chat input ── */}
            <div className="border-t border-gray-800 px-6 py-4">
              <ChatInput
                onSend={handleChatMessage}
                loading={loading}
                disabled={selectedDoc.status !== 'READY'}
              />
            </div>
          </>
        ) : (
          <EmptyState />
        )}
      </main>
    </div>
  )
}

/* ── Feed item dispatcher ── */

const ACTION_META = {
  summary:    { label: 'Summary',    color: 'text-indigo-400',  dot: 'bg-indigo-500' },
  extraction: { label: 'Extraction', color: 'text-purple-400',  dot: 'bg-purple-500' },
  comparison: { label: 'Comparison', color: 'text-amber-400',   dot: 'bg-amber-500'  },
}

function FeedItem({ item }) {
  // Chat messages
  if (item.type === 'user' || item.type === 'assistant') {
    return (
      <ChatMessage message={{ role: item.type, content: item.content, citations: item.citations }} />
    )
  }

  // Analysis results
  const meta = ACTION_META[item.type] ?? { label: item.type, color: 'text-gray-400', dot: 'bg-gray-500' }
  return (
    <div>
      {/* Label row */}
      <div className="mb-2.5 flex items-center gap-2">
        <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} />
        <span className={`text-xs font-semibold uppercase tracking-wide ${meta.color}`}>
          {meta.label}
        </span>
        {item.timestamp && (
          <span className="text-xs text-gray-600">
            · {item.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </span>
        )}
      </div>
      <ResultPanel result={item} />
    </div>
  )
}

/* ── Small helpers ── */

function LoadingDots() {
  return (
    <div className="flex items-center gap-2 py-1">
      <div className="flex gap-1">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-1.5 w-1.5 animate-bounce rounded-full bg-indigo-400"
            style={{ animationDelay: `${i * 150}ms` }}
          />
        ))}
      </div>
      <span className="text-xs text-gray-500">Thinking…</span>
    </div>
  )
}

function EmptyFeedState({ docReady }) {
  return (
    <div className="flex h-full min-h-64 flex-col items-center justify-center gap-3 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-900/40">
        {docReady
          ? <MessageSquare size={22} className="text-indigo-400" />
          : <Sparkles size={22} className="text-gray-600" />}
      </div>
      <div>
        {docReady ? (
          <>
            <p className="text-sm font-medium text-gray-300">Ask anything, or use the sidebar actions</p>
            <p className="mt-1 text-xs text-gray-500">
              Chat · Summarize · Extract info · Compare — all results appear here
            </p>
          </>
        ) : (
          <>
            <p className="text-sm font-medium text-gray-400">Document is processing…</p>
            <p className="mt-1 text-xs text-gray-500">Wait a moment and try again</p>
          </>
        )}
      </div>
    </div>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gray-800">
        <FileText size={28} className="text-gray-600" />
      </div>
      <div>
        <p className="text-base font-medium text-gray-300">No document selected</p>
        <p className="mt-1 text-sm text-gray-500">
          Upload a PDF, DOCX, or TXT file and select it to get started
        </p>
      </div>
    </div>
  )
}

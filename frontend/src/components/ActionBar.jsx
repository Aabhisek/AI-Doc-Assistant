import { useState } from 'react'
import { FileText, List, GitCompare, Loader2, ChevronDown } from 'lucide-react'
import { summaryApi, extractionApi, comparisonApi } from '../services/api'

const EXTRACTION_CATEGORIES = [
  'Key dates',
  'Names and people',
  'Skills and technologies',
  'Action items',
  'Requirements',
  'Financial figures',
  'Locations',
]

/**
 * Sidebar action buttons for document analysis operations.
 * Results are passed up to App.jsx via onResult(), which switches the main
 * panel to the "Analyze" tab and displays the ResultPanel.
 */
export default function ActionBar({ documents, selectedDocId, onResult, setLoading, setError }) {
  const [extractOpen, setExtractOpen] = useState(false)
  const [compareOpen, setCompareOpen] = useState(false)
  const [compareDocId, setCompareDocId] = useState('')

  const readyDocs = documents.filter((d) => d.id !== selectedDocId && d.status === 'READY')

  const runAction = async (label, apiFn) => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await apiFn()
      onResult({ type: label, data })
    } catch (err) {
      setError(err.response?.data?.error ?? `${label} failed`)
    } finally {
      setLoading(false)
    }
  }

  const handleSummarize = () =>
    runAction('summary', () => summaryApi.summarize(selectedDocId))

  const handleExtract = (category) => {
    setExtractOpen(false)
    runAction('extraction', () => extractionApi.extract(selectedDocId, category))
  }

  const handleCompare = () => {
    if (!compareDocId) return
    setCompareOpen(false)
    runAction('comparison', () => comparisonApi.compare(selectedDocId, compareDocId))
  }

  return (
    <div className="space-y-2">
      <p className="text-xs font-medium uppercase tracking-wider text-gray-600">Analyze</p>

      {/* Summarize */}
      <ActionButton
        icon={<FileText size={13} />}
        label="Summarize"
        onClick={handleSummarize}
      />

      {/* Extract — dropdown with preset categories */}
      <div className="relative">
        <ActionButton
          icon={<List size={13} />}
          label="Extract info"
          trailingIcon={<ChevronDown size={12} />}
          onClick={() => {
            setExtractOpen((v) => !v)
            setCompareOpen(false)
          }}
        />
        {extractOpen && (
          <div className="absolute bottom-full left-0 z-10 mb-1 w-full rounded-xl border border-gray-700 bg-gray-800 py-1 shadow-xl">
            {EXTRACTION_CATEGORIES.map((cat) => (
              <button
                key={cat}
                onClick={() => handleExtract(cat)}
                className="w-full px-3 py-1.5 text-left text-xs text-gray-300 hover:bg-gray-700 hover:text-white transition-colors"
              >
                {cat}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Compare — dropdown to pick a second document */}
      <div className="relative">
        <ActionButton
          icon={<GitCompare size={13} />}
          label="Compare docs"
          trailingIcon={<ChevronDown size={12} />}
          onClick={() => {
            setCompareOpen((v) => !v)
            setExtractOpen(false)
          }}
          disabled={readyDocs.length === 0}
        />
        {compareOpen && (
          <div className="absolute bottom-full left-0 z-10 mb-1 w-full rounded-xl border border-gray-700 bg-gray-800 p-3 shadow-xl">
            {readyDocs.length === 0 ? (
              <p className="text-xs text-gray-500">Upload another document first</p>
            ) : (
              <>
                <p className="mb-2 text-xs text-gray-500">Compare with:</p>
                <select
                  value={compareDocId}
                  onChange={(e) => setCompareDocId(e.target.value)}
                  className="mb-2 w-full rounded-lg border border-gray-600 bg-gray-900 px-2 py-1.5 text-xs text-gray-200 outline-none"
                >
                  <option value="">Select document…</option>
                  {readyDocs.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </select>
                <button
                  onClick={handleCompare}
                  disabled={!compareDocId}
                  className="w-full rounded-lg bg-indigo-600 py-1.5 text-xs font-medium text-white transition-colors hover:bg-indigo-500 disabled:opacity-40"
                >
                  Compare
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function ActionButton({ icon, label, trailingIcon, onClick, disabled }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-gray-300 transition-colors hover:bg-gray-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
    >
      <span className="text-gray-500">{icon}</span>
      <span className="flex-1 text-left">{label}</span>
      {trailingIcon && <span className="text-gray-600">{trailingIcon}</span>}
    </button>
  )
}

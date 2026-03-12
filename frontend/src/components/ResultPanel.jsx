import ReactMarkdown from 'react-markdown'
import { FileText, List, GitCompare } from 'lucide-react'

/**
 * Displays the result of an AI analysis action (summary, extraction, comparison).
 * Each action type has its own visual layout.
 */
export default function ResultPanel({ result }) {
  if (!result) return null

  switch (result.type) {
    case 'summary':
      return <SummaryResult data={result.data} />
    case 'extraction':
      return <ExtractionResult data={result.data} />
    case 'comparison':
      return <ComparisonResult data={result.data} />
    default:
      return null
  }
}

function SectionHeader({ icon, title }) {
  return (
    <div className="mb-3 flex items-center gap-2">
      <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-900/40">
        <span className="text-indigo-400">{icon}</span>
      </div>
      <h3 className="text-sm font-semibold text-white">{title}</h3>
    </div>
  )
}

function Card({ children, className = '' }) {
  return (
    <div className={`rounded-xl border border-gray-800 bg-gray-900/60 p-4 ${className}`}>
      {children}
    </div>
  )
}

function SummaryResult({ data }) {
  return (
    <div className="space-y-4">
      <SectionHeader icon={<FileText size={14} />} title="Document Summary" />

      <Card>
        <p className="mb-1 text-xs font-medium uppercase tracking-wide text-gray-500">Summary</p>
        <p className="text-sm leading-relaxed text-gray-200">{data.summary}</p>
      </Card>

      {data.keyPoints && (
        <Card>
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-gray-500">Key Points</p>
          <div className="prose prose-invert prose-sm max-w-none prose-li:my-0.5 prose-ul:my-0">
            <ReactMarkdown>{data.keyPoints}</ReactMarkdown>
          </div>
        </Card>
      )}
    </div>
  )
}

function ExtractionResult({ data }) {
  return (
    <div className="space-y-4">
      <SectionHeader icon={<List size={14} />} title={`Extracted: ${data.category}`} />

      <Card>
        {data.items.length === 0 ? (
          <p className="text-sm text-gray-500">No items found for this category.</p>
        ) : (
          <ul className="space-y-2">
            {data.items.map((item, i) => (
              <li key={i} className="flex items-start gap-2.5">
                <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-500" />
                <span className="text-sm text-gray-200">{item}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

function ComparisonResult({ data }) {
  return (
    <div className="space-y-4">
      <SectionHeader icon={<GitCompare size={14} />} title="Document Comparison" />

      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-emerald-500">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            Similarities
          </p>
          <div className="prose prose-invert prose-sm max-w-none prose-li:my-0.5 prose-ul:my-0">
            <ReactMarkdown>{data.similarities || '_None identified_'}</ReactMarkdown>
          </div>
        </Card>

        <Card>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-amber-500">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
            Differences
          </p>
          <div className="prose prose-invert prose-sm max-w-none prose-li:my-0.5 prose-ul:my-0">
            <ReactMarkdown>{data.differences || '_None identified_'}</ReactMarkdown>
          </div>
        </Card>
      </div>
    </div>
  )
}

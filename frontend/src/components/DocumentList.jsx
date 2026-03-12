import { useState } from 'react'
import { FileText, Trash2, Loader2, CheckCircle, XCircle, Clock } from 'lucide-react'

const STATUS_CONFIG = {
  READY: {
    icon: <CheckCircle size={12} className="text-emerald-400" />,
    label: 'Ready',
    color: 'text-emerald-400',
  },
  PROCESSING: {
    icon: <Loader2 size={12} className="animate-spin text-amber-400" />,
    label: 'Indexing…',
    color: 'text-amber-400',
  },
  UPLOADING: {
    icon: <Clock size={12} className="text-blue-400" />,
    label: 'Uploading',
    color: 'text-blue-400',
  },
  FAILED: {
    icon: <XCircle size={12} className="text-red-400" />,
    label: 'Failed',
    color: 'text-red-400',
  },
}

export default function DocumentList({ documents, selectedDocId, onSelect, onDelete }) {
  if (documents.length === 0) {
    return (
      <div className="px-4 py-6 text-center">
        <p className="text-xs text-gray-600">No documents yet</p>
      </div>
    )
  }

  return (
    <div className="px-3 py-2">
      <p className="mb-2 px-2 text-xs font-medium uppercase tracking-wider text-gray-600">
        Documents ({documents.length})
      </p>
      <ul className="space-y-0.5">
        {documents.map((doc) => (
          <DocumentItem
            key={doc.id}
            doc={doc}
            selected={doc.id === selectedDocId}
            onSelect={onSelect}
            onDelete={onDelete}
          />
        ))}
      </ul>
    </div>
  )
}

function DocumentItem({ doc, selected, onSelect, onDelete }) {
  const [hovered, setHovered] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const status = STATUS_CONFIG[doc.status] ?? STATUS_CONFIG.FAILED

  const handleDelete = async (e) => {
    e.stopPropagation()
    setDeleting(true)
    await onDelete(doc.id)
    setDeleting(false)
  }

  return (
    <li
      onClick={() => onSelect(doc.id)}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className={`group flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2.5 transition-colors ${
        selected
          ? 'bg-indigo-900/40 text-white'
          : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
      }`}
    >
      <FileText size={14} className={selected ? 'text-indigo-400' : 'text-gray-600'} />

      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-medium leading-tight" title={doc.name}>
          {doc.name}
        </p>
        <div className="mt-0.5 flex items-center gap-1">
          {status.icon}
          <span className={`text-xs ${status.color}`}>{status.label}</span>
        </div>
      </div>

      {/* Delete button — only shown on hover */}
      {hovered && !deleting && (
        <button
          onClick={handleDelete}
          className="rounded p-0.5 text-gray-600 transition-colors hover:bg-red-900/40 hover:text-red-400"
          title="Delete document"
        >
          <Trash2 size={13} />
        </button>
      )}
      {deleting && <Loader2 size={13} className="animate-spin text-gray-600" />}
    </li>
  )
}

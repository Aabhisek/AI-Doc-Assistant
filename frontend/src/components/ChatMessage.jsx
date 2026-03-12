import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import { User, Bot, ChevronDown, ChevronUp, Quote } from 'lucide-react'

/**
 * Renders a single chat message bubble.
 * User messages are right-aligned; assistant messages are left-aligned.
 * Assistant messages include a collapsible citations panel.
 */
export default function ChatMessage({ message }) {
  const isUser = message.role === 'user'

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {/* Avatar */}
      <div
        className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${
          isUser ? 'bg-indigo-700' : 'bg-gray-700'
        }`}
      >
        {isUser ? (
          <User size={13} className="text-indigo-200" />
        ) : (
          <Bot size={13} className="text-gray-300" />
        )}
      </div>

      {/* Bubble */}
      <div className={`flex max-w-[80%] flex-col gap-2 ${isUser ? 'items-end' : 'items-start'}`}>
        <div
          className={`rounded-2xl px-4 py-2.5 text-sm leading-relaxed ${
            isUser
              ? 'rounded-tr-sm bg-indigo-700 text-white'
              : 'rounded-tl-sm bg-gray-800 text-gray-100'
          }`}
        >
          {isUser ? (
            <p>{message.content}</p>
          ) : (
            <div className="prose prose-invert prose-sm max-w-none prose-p:my-1 prose-ul:my-1 prose-li:my-0">
              <ReactMarkdown>{message.content}</ReactMarkdown>
            </div>
          )}
        </div>

        {/* Citations */}
        {!isUser && message.citations?.length > 0 && (
          <CitationsPanel citations={message.citations} />
        )}
      </div>
    </div>
  )
}

function CitationsPanel({ citations }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="w-full">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300"
      >
        <Quote size={11} />
        {citations.length} {citations.length === 1 ? 'source' : 'sources'}
        {open ? <ChevronUp size={11} /> : <ChevronDown size={11} />}
      </button>

      {open && (
        <div className="mt-1 space-y-2">
          {citations.map((c, i) => (
            <div
              key={i}
              className="rounded-xl border border-gray-700/50 bg-gray-800/50 px-3 py-2.5"
            >
              <div className="mb-1.5 flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-gray-400 truncate">{c.documentName}</span>
                {c.score > 0 && (
                  <span className="shrink-0 rounded-full bg-indigo-900/40 px-2 py-0.5 text-xs text-indigo-400">
                    {(c.score * 100).toFixed(0)}% match
                  </span>
                )}
              </div>
              <p className="text-xs leading-relaxed text-gray-500 line-clamp-3">{c.excerpt}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

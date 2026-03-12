import { useState, useRef, useEffect } from 'react'
import { Send, Lock } from 'lucide-react'
import { chatApi } from '../services/api'

/**
 * Chat text input with auto-expanding textarea.
 * Sends on Enter (Shift+Enter for newline).
 * Disabled when no READY document is selected.
 */
export default function ChatInput({ onSend, loading, disabled }) {
  const [value, setValue] = useState('')
  const textareaRef = useRef(null)

  // Auto-resize the textarea as the user types
  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 160) + 'px'
  }, [value])

  const handleSubmit = () => {
    const question = value.trim()
    if (!question || loading || disabled) return
    setValue('')
    onSend(question, chatApi.ask)
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  return (
    <div className="flex items-end gap-3">
      <div className="flex-1 rounded-xl border border-gray-700 bg-gray-800 px-4 py-3 focus-within:border-indigo-600 transition-colors">
        {disabled ? (
          <div className="flex items-center gap-2 text-gray-600">
            <Lock size={13} />
            <span className="text-sm">Select a ready document to start chatting</span>
          </div>
        ) : (
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask a question about this document…"
            rows={1}
            className="w-full resize-none bg-transparent text-sm text-gray-100 placeholder-gray-600 outline-none"
          />
        )}
      </div>

      <button
        onClick={handleSubmit}
        disabled={!value.trim() || loading || disabled}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white transition-all hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        title="Send (Enter)"
      >
        <Send size={16} />
      </button>
    </div>
  )
}

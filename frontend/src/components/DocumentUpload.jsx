import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { Upload, Loader2, CheckCircle, AlertCircle } from 'lucide-react'
import { documentApi } from '../services/api'

const ACCEPTED_TYPES = {
  'application/pdf': ['.pdf'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
  'text/plain': ['.txt'],
}

/**
 * Drag-and-drop file upload zone.
 * Uploads the file via POST /api/documents/upload and reports back the
 * DocumentResponse (including final status) once the call completes.
 *
 * Ingestion is synchronous on the backend, so the request blocks until the
 * document is fully indexed (or fails). We show a spinner while waiting.
 */
export default function DocumentUpload({ onUploaded }) {
  const [state, setState] = useState('idle') // idle | uploading | success | error
  const [errorMsg, setErrorMsg] = useState('')

  const handleDrop = useCallback(
    async (acceptedFiles) => {
      if (acceptedFiles.length === 0) return

      const file = acceptedFiles[0]
      setState('uploading')
      setErrorMsg('')

      try {
        const { data } = await documentApi.upload(file)
        setState('success')
        onUploaded(data)
        // Reset back to idle after a brief success flash
        setTimeout(() => setState('idle'), 2000)
      } catch (err) {
        const msg = err.response?.data?.error ?? 'Upload failed. Please try again.'
        setState('error')
        setErrorMsg(msg)
      }
    },
    [onUploaded]
  )

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: handleDrop,
    accept: ACCEPTED_TYPES,
    maxFiles: 1,
    disabled: state === 'uploading',
  })

  return (
    <div
      {...getRootProps()}
      className={`flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed px-4 py-5 text-center transition-all ${
        isDragActive
          ? 'border-indigo-500 bg-indigo-900/20'
          : state === 'success'
          ? 'border-emerald-600 bg-emerald-900/20'
          : state === 'error'
          ? 'border-red-700 bg-red-900/20'
          : 'border-gray-700 bg-gray-800/50 hover:border-gray-600 hover:bg-gray-800'
      }`}
    >
      <input {...getInputProps()} />

      {state === 'uploading' ? (
        <>
          <Loader2 size={20} className="animate-spin text-indigo-400" />
          <p className="text-xs text-gray-400">Uploading and indexing…</p>
        </>
      ) : state === 'success' ? (
        <>
          <CheckCircle size={20} className="text-emerald-400" />
          <p className="text-xs text-emerald-400">Document ready!</p>
        </>
      ) : state === 'error' ? (
        <>
          <AlertCircle size={20} className="text-red-400" />
          <p className="text-xs text-red-400">{errorMsg}</p>
          <p className="text-xs text-gray-500">Click to try again</p>
        </>
      ) : (
        <>
          <Upload size={20} className={isDragActive ? 'text-indigo-400' : 'text-gray-500'} />
          <div>
            <p className="text-xs font-medium text-gray-300">
              {isDragActive ? 'Drop file here' : 'Upload document'}
            </p>
            <p className="mt-0.5 text-xs text-gray-500">PDF, DOCX, or TXT</p>
          </div>
        </>
      )}
    </div>
  )
}

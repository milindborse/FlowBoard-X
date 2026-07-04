import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Zap, Loader2 } from 'lucide-react'
import { authApi } from '../api/workflows'
import { useAuthStore } from '../store/authStore'

export default function Login() {
  const navigate = useNavigate()
  const { setUser } = useAuthStore()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')

  const mutation = useMutation({
    mutationFn: () => mode === 'login'
      ? authApi.login(email, password)
      : authApi.register(email, password, displayName),
    onSuccess: data => { setUser(data); navigate('/') },
    onError: (e: any) => setError(e?.response?.data?.error ?? 'Something went wrong'),
  })

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <div className="flex items-center justify-center gap-2.5 mb-8">
          <div className="w-9 h-9 bg-brand-600 rounded-xl flex items-center justify-center">
            <Zap size={18} className="text-white" />
          </div>
          <span className="font-bold text-xl text-gray-900">FlowBoard<span className="text-brand-600"> X</span></span>
        </div>

        <div className="card">
          <h1 className="text-lg font-bold text-gray-900 mb-1">
            {mode === 'login' ? 'Welcome back' : 'Create your account'}
          </h1>
          <p className="text-sm text-gray-500 mb-6">
            {mode === 'login' ? 'Sign in to manage your workflows' : 'Start orchestrating workflows in minutes'}
          </p>

          <form onSubmit={e => { e.preventDefault(); setError(''); mutation.mutate() }} className="space-y-4">
            {mode === 'register' && (
              <div>
                <label className="label">Display Name</label>
                <input className="input" value={displayName} onChange={e => setDisplayName(e.target.value)} required />
              </div>
            )}
            <div>
              <label className="label">Email</label>
              <input type="email" className="input" value={email} onChange={e => setEmail(e.target.value)} required />
            </div>
            <div>
              <label className="label">Password</label>
              <input type="password" className="input" value={password} onChange={e => setPassword(e.target.value)} required minLength={6} />
            </div>

            {error && <p className="text-xs text-red-600 bg-red-50 px-3 py-2 rounded-lg">{error}</p>}

            <button type="submit" disabled={mutation.isPending} className="btn-primary w-full justify-center">
              {mutation.isPending ? <Loader2 size={15} className="animate-spin" /> : null}
              {mode === 'login' ? 'Sign In' : 'Create Account'}
            </button>
          </form>

          <p className="text-xs text-gray-500 text-center mt-5">
            {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
            <button
              onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
              className="text-brand-600 font-medium hover:underline"
            >
              {mode === 'login' ? 'Sign up' : 'Sign in'}
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}

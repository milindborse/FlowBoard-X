import { create } from 'zustand'
import type { AuthUser } from '../types'

interface AuthState {
  user: AuthUser | null
  setUser: (user: AuthUser | null) => void
  logout: () => void
}

const stored = localStorage.getItem('fbx_user')

export const useAuthStore = create<AuthState>(set => ({
  user: stored ? JSON.parse(stored) : null,
  setUser: user => {
    if (user) localStorage.setItem('fbx_user', JSON.stringify(user))
    else localStorage.removeItem('fbx_user')
    set({ user })
  },
  logout: () => {
    localStorage.removeItem('fbx_user')
    set({ user: null })
  },
}))

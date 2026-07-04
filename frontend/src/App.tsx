import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import Workflows from './pages/Workflows'
import Builder from './pages/Builder'
import RunViewer from './pages/RunViewer'
import History from './pages/History'
import Analytics from './pages/Analytics'
import Settings from './pages/Settings'
import Login from './pages/Login'
import { useAuthStore } from './store/authStore'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 10_000 } },
})

function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const { user } = useAuthStore()
  if (!user) return <Navigate to="/login" replace />
  return <Layout>{children}</Layout>
}

function ProtectedFullScreen({ children }: { children: React.ReactNode }) {
  const { user } = useAuthStore()
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<ProtectedLayout><Dashboard /></ProtectedLayout>} />
          <Route path="/workflows" element={<ProtectedLayout><Workflows /></ProtectedLayout>} />
          <Route path="/workflows/new" element={<Navigate to="/workflows" replace />} />
          <Route path="/builder/:id" element={<ProtectedFullScreen><Builder /></ProtectedFullScreen>} />
          <Route path="/runs/:runId" element={<ProtectedFullScreen><RunViewer /></ProtectedFullScreen>} />
          <Route path="/history" element={<ProtectedLayout><History /></ProtectedLayout>} />
          <Route path="/analytics" element={<ProtectedLayout><Analytics /></ProtectedLayout>} />
          <Route path="/settings" element={<ProtectedLayout><Settings /></ProtectedLayout>} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

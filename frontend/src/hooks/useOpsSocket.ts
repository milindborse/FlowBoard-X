import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { QueueMetrics, QueueActivityEvent } from '../types'

interface UseOpsSocketOptions {
  /** When false, the socket is torn down and no reconnect attempts happen (powers the "Pause Live Updates" toolbar button). */
  enabled: boolean
  onMetrics: (snapshot: QueueMetrics) => void
  onActivity: (event: QueueActivityEvent) => void
}

/**
 * Subscribes to /topic/ops/queue (periodic full snapshot) and /topic/ops/activity
 * (live event-by-event feed). Separate from RunViewer's per-run STOMP client entirely.
 *
 * Unlike the original RunViewer socket, this one surfaces connection failures instead
 * of failing silently - onStompError/onWebSocketError are logged so a broken connection
 * is actually visible in the console instead of just quietly never delivering events.
 */
export function useOpsSocket({ enabled, onMetrics, onActivity }: UseOpsSocketOptions) {
  const [connected, setConnected] = useState(false)
  const [lastError, setLastError] = useState<string | null>(null)
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    if (!enabled) {
      clientRef.current?.deactivate()
      clientRef.current = null
      setConnected(false)
      return
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      debug: (msg) => {
        // Visible in console under a clear prefix, instead of STOMP's default silence.
        if (msg.toLowerCase().includes('error')) console.warn('[ops-socket]', msg)
      },
      onConnect: () => {
        setConnected(true)
        setLastError(null)
        client.subscribe('/topic/ops/queue', (msg) => {
          try { onMetrics(JSON.parse(msg.body)) } catch (e) { console.error('[ops-socket] bad metrics payload', e) }
        })
        client.subscribe('/topic/ops/activity', (msg) => {
          try { onActivity(JSON.parse(msg.body)) } catch (e) { console.error('[ops-socket] bad activity payload', e) }
        })
      },
      onStompError: (frame) => {
        const detail = frame.headers?.message || 'Unknown STOMP error'
        console.error('[ops-socket] STOMP error:', detail, frame.body)
        setLastError(detail)
        setConnected(false)
      },
      onWebSocketError: (event) => {
        console.error('[ops-socket] WebSocket error:', event)
        setLastError('WebSocket connection failed - check that the backend is running and /ws is reachable.')
        setConnected(false)
      },
      onDisconnect: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => { client.deactivate() }
  }, [enabled])

  return { connected, lastError }
}
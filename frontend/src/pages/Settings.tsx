import React, { useState } from 'react'
import { User, Bell, Key, Webhook, Save } from 'lucide-react'
import { useAuthStore } from '../store/authStore'

const tabs = [
  { key: 'profile',      label: 'Profile',      icon: User },
  { key: 'notifications',label: 'Notifications', icon: Bell },
  { key: 'api',          label: 'API Keys',      icon: Key },
  { key: 'webhooks',     label: 'Webhooks',      icon: Webhook },
]

export default function Settings() {
  const [tab, setTab] = useState('profile')
  const { user } = useAuthStore()

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500 mt-1">Manage your account, integrations, and preferences</p>
      </div>

      <div className="flex gap-8">
        {/* Tabs */}
        <div className="w-48 space-y-1 flex-shrink-0">
          {tabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                tab === key ? 'bg-brand-50 text-brand-700' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <Icon size={15} />
              {label}
            </button>
          ))}
        </div>

        {/* Panel */}
        <div className="flex-1 card">
          {tab === 'profile' && (
            <div className="space-y-5">
              <h3 className="text-sm font-semibold text-gray-900">Profile Information</h3>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="label">Display Name</label>
                  <input className="input" defaultValue={user?.displayName} />
                </div>
                <div>
                  <label className="label">Email</label>
                  <input className="input" defaultValue={user?.email} disabled />
                </div>
              </div>
              <div>
                <label className="label">Role</label>
                <input className="input bg-gray-50" defaultValue={user?.role} disabled />
              </div>
              <button className="btn-primary"><Save size={14} /> Save Changes</button>
            </div>
          )}

          {tab === 'notifications' && (
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-gray-900 mb-2">Notification Preferences</h3>
              {[
                ['Workflow run failures', 'Get notified when any workflow run fails'],
                ['Approval required', 'Get notified when a workflow pauses for human approval'],
                ['Weekly digest', 'Receive a weekly summary of execution analytics'],
              ].map(([title, desc]) => (
                <div key={title} className="flex items-center justify-between py-3 border-b border-gray-50 last:border-0">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{title}</p>
                    <p className="text-xs text-gray-400">{desc}</p>
                  </div>
                  <input type="checkbox" defaultChecked className="w-4 h-4 accent-brand-600" />
                </div>
              ))}
            </div>
          )}

          {tab === 'api' && (
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-gray-900 mb-2">API Keys</h3>
              <p className="text-xs text-gray-500 mb-4">
                Use API keys to trigger workflows programmatically or integrate with external systems.
              </p>
              <div className="p-4 bg-gray-50 rounded-lg border border-gray-100 flex items-center justify-between">
                <div>
                  <p className="text-sm font-mono text-gray-700">fbx_live_••••••••••••3f9a</p>
                  <p className="text-xs text-gray-400 mt-0.5">Created 12 days ago · Last used 2h ago</p>
                </div>
                <button className="btn-secondary text-xs">Revoke</button>
              </div>
              <button className="btn-primary text-sm">Generate New Key</button>
            </div>
          )}

          {tab === 'webhooks' && (
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-gray-900 mb-2">Incoming Webhooks</h3>
              <p className="text-xs text-gray-500 mb-4">
                Each workflow with a Webhook Trigger node exposes an endpoint at:
              </p>
              <code className="block px-4 py-3 bg-gray-900 text-emerald-400 rounded-lg text-xs font-mono">
                POST /webhooks/&#123;workflowId&#125;
              </code>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

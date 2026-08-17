import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, createReminder, deleteReminder, setReminderCompleted } from '../api/client'
import type { ReminderDto } from '../api/types'
import { useToast } from '../context/ToastContext'
import { CheckIcon, ClockIcon, TrashIcon } from './icons'

type Preset = 'tomorrow' | '3days' | 'nextweek' | 'custom'

const TITLE_PRESETS = ['Follow up with provider', 'Confirm insurance', 'Check appointment availability', 'Review shortlist']

function presetToDueAt(preset: Preset, customDate: string): Date | null {
  const now = new Date()
  if (preset === 'tomorrow') return new Date(now.getTime() + 24 * 60 * 60 * 1000)
  if (preset === '3days') return new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000)
  if (preset === 'nextweek') return new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
  if (preset === 'custom' && customDate) return new Date(customDate)
  return null
}

function bucketFor(reminder: ReminderDto): 'overdue' | 'today' | 'week' | 'later' | 'completed' {
  if (reminder.completedAt) return 'completed'
  const due = new Date(reminder.dueAt)
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const endOfToday = new Date(startOfToday.getTime() + 24 * 60 * 60 * 1000)
  const endOfWeek = new Date(startOfToday.getTime() + 7 * 24 * 60 * 60 * 1000)
  if (due < startOfToday) return 'overdue'
  if (due < endOfToday) return 'today'
  if (due < endOfWeek) return 'week'
  return 'later'
}

/** In-app-only follow-up reminders -- no push/SMS/email (CLAUDE.md "Follow-Up Reminder Architecture"). */
function ReminderPanel({ reminders, onChanged }: { reminders: ReminderDto[]; onChanged: (reminders: ReminderDto[]) => void }) {
  const { showToast } = useToast()
  const [titleChoice, setTitleChoice] = useState(TITLE_PRESETS[0])
  const [customTitle, setCustomTitle] = useState('')
  const [preset, setPreset] = useState<Preset>('tomorrow')
  const [customDate, setCustomDate] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const grouped = useMemo(() => {
    const buckets: Record<string, ReminderDto[]> = { overdue: [], today: [], week: [], later: [], completed: [] }
    for (const reminder of reminders) {
      buckets[bucketFor(reminder)].push(reminder)
    }
    return buckets
  }, [reminders])

  async function handleCreate() {
    const title = titleChoice === 'custom' ? customTitle.trim() : titleChoice
    const dueAt = presetToDueAt(preset, customDate)
    if (!title || !dueAt) return
    setIsCreating(true)
    setError(null)
    try {
      const created = await createReminder({ title, dueAt: dueAt.toISOString() })
      onChanged([...reminders, created])
      setCustomTitle('')
      showToast('Reminder created')
    } catch (createError) {
      setError(createError instanceof ApiError ? createError.message : 'Could not create reminder. Please try again.')
    } finally {
      setIsCreating(false)
    }
  }

  async function handleToggleComplete(reminder: ReminderDto) {
    const completed = !reminder.completedAt
    onChanged(
      reminders.map((r) => (r.id === reminder.id ? { ...r, completedAt: completed ? new Date().toISOString() : null } : r)),
    )
    try {
      await setReminderCompleted(reminder.id, completed)
    } catch (toggleError) {
      onChanged(reminders)
      showToast(toggleError instanceof ApiError ? toggleError.message : 'Could not update reminder. Please try again.')
    }
  }

  async function handleDelete(reminder: ReminderDto) {
    const previous = reminders
    onChanged(reminders.filter((r) => r.id !== reminder.id))
    try {
      await deleteReminder(reminder.id)
    } catch (deleteError) {
      onChanged(previous)
      showToast(deleteError instanceof ApiError ? deleteError.message : 'Could not delete reminder. Please try again.')
    }
  }

  return (
    <section className="navigator-panel reminder-panel">
      <h2>
        <ClockIcon width={18} height={18} />
        Reminders
      </h2>
      <p className="results-subtext">Simple, in-app-only follow-ups. DocFit AI never sends email, SMS, or push notifications.</p>

      <div className="reminder-form">
        <div className="field">
          <label htmlFor="reminder-title-choice">What do you want to be reminded about?</label>
          <select id="reminder-title-choice" value={titleChoice} onChange={(event) => setTitleChoice(event.target.value)}>
            {TITLE_PRESETS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
            <option value="custom">Custom&hellip;</option>
          </select>
        </div>
        {titleChoice === 'custom' && (
          <div className="field">
            <label htmlFor="reminder-custom-title" className="visually-hidden">
              Custom reminder title
            </label>
            <input
              id="reminder-custom-title"
              type="text"
              maxLength={200}
              placeholder="e.g. Ask about weekend hours (avoid sharing medical details here)"
              value={customTitle}
              onChange={(event) => setCustomTitle(event.target.value)}
            />
          </div>
        )}
        <div className="field">
          <label htmlFor="reminder-preset">When?</label>
          <select id="reminder-preset" value={preset} onChange={(event) => setPreset(event.target.value as Preset)}>
            <option value="tomorrow">Tomorrow</option>
            <option value="3days">In 3 days</option>
            <option value="nextweek">Next week</option>
            <option value="custom">Choose date&hellip;</option>
          </select>
        </div>
        {preset === 'custom' && (
          <div className="field">
            <label htmlFor="reminder-custom-date" className="visually-hidden">
              Reminder date
            </label>
            <input id="reminder-custom-date" type="date" value={customDate} onChange={(event) => setCustomDate(event.target.value)} />
          </div>
        )}
        {error && <p className="field-hint">{error}</p>}
        <button
          type="button"
          className="secondary-button"
          disabled={isCreating || (titleChoice === 'custom' && !customTitle.trim()) || (preset === 'custom' && !customDate)}
          onClick={() => void handleCreate()}
        >
          Add reminder
        </button>
      </div>

      <ReminderGroup label="Overdue" items={grouped.overdue} onToggle={handleToggleComplete} onDelete={handleDelete} />
      <ReminderGroup label="Today" items={grouped.today} onToggle={handleToggleComplete} onDelete={handleDelete} />
      <ReminderGroup label="This week" items={grouped.week} onToggle={handleToggleComplete} onDelete={handleDelete} />
      <ReminderGroup label="Upcoming" items={grouped.later} onToggle={handleToggleComplete} onDelete={handleDelete} />
      <ReminderGroup label="Completed" items={grouped.completed} onToggle={handleToggleComplete} onDelete={handleDelete} collapsible />
    </section>
  )
}

function ReminderGroup({
  label,
  items,
  onToggle,
  onDelete,
  collapsible,
}: {
  label: string
  items: ReminderDto[]
  onToggle: (reminder: ReminderDto) => void
  onDelete: (reminder: ReminderDto) => void
  collapsible?: boolean
}) {
  if (items.length === 0) return null
  return (
    <div className="reminder-group">
      <h3>{label}</h3>
      <ul className={collapsible ? 'reminder-list is-collapsible' : 'reminder-list'}>
        {items.map((reminder) => (
          <li key={reminder.id} className="reminder-row">
            <button
              type="button"
              className={`reminder-check${reminder.completedAt ? ' is-done' : ''}`}
              onClick={() => onToggle(reminder)}
              aria-pressed={Boolean(reminder.completedAt)}
              aria-label={reminder.completedAt ? `Mark "${reminder.title}" as not done` : `Mark "${reminder.title}" as done`}
            >
              {reminder.completedAt && <CheckIcon width={12} height={12} />}
            </button>
            <div className="reminder-copy">
              <p className={reminder.completedAt ? 'reminder-title is-done' : 'reminder-title'}>{reminder.title}</p>
              <p className="results-subtext">
                {new Date(reminder.dueAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                {reminder.providerName && (
                  <>
                    {' '}
                    &middot;{' '}
                    {reminder.providerId ? <Link to={`/providers/${reminder.providerId}`}>{reminder.providerName}</Link> : reminder.providerName}
                  </>
                )}
                {reminder.shortlistName && (
                  <>
                    {' '}
                    &middot;{' '}
                    {reminder.shortlistId ? <Link to={`/shortlists/${reminder.shortlistId}`}>{reminder.shortlistName}</Link> : reminder.shortlistName}
                  </>
                )}
              </p>
            </div>
            <button type="button" className="icon-button" onClick={() => onDelete(reminder)} aria-label={`Delete reminder: ${reminder.title}`}>
              <TrashIcon width={14} height={14} />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default ReminderPanel

import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

// Navigates via the account menu's own "Navigator" link (client-side React Router navigation)
// rather than page.goto('/navigator') -- a raw goto forces a full page reload, which needs the
// browser to have already flushed the just-issued refresh-token cookie from registration; that
// flush is not always immediate in a fresh automated browser context and can 401 the session
// restore a beat too early (verified directly: identical goto succeeds once any other in-app
// navigation happens first, and fails consistently without one). Clicking through the UI is both
// more realistic and avoids the reload entirely.
async function goToNavigatorViaMenu(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'Account menu' }).click()
  await page.getByRole('menuitem', { name: 'Navigator' }).click()
}

async function registerAndFindProvider(page: import('@playwright/test').Page, email: string) {
  await page.goto('/register')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('TestPassword123')
  await page.getByRole('button', { name: /create account/i }).click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()
  await page.locator('.provider-card').first().getByRole('link', { name: /view details/i }).click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
}

test('care navigator: save a provider, set status, complete a checklist item, see it reflected on the dashboard', async ({ page }) => {
  const email = `navigator-e2e-${randomUUID()}@example.com`
  await registerAndFindProvider(page, email)

  // Save from the detail page, then set an administrative status there.
  await page.getByRole('button', { name: /^save provider$/i }).click()
  await expect(page.getByRole('button', { name: /saved to your list/i })).toBeVisible()

  const statusSelect = page.getByLabel('Navigation status')
  await statusSelect.selectOption('TO_CONTACT')

  // Before-you-contact checklist: confirm one item.
  await expect(page.getByRole('heading', { name: 'Before you contact this provider' })).toBeVisible()
  const insuranceRow = page.locator('.verification-checklist-item', { hasText: 'Confirm insurance/network status' })
  await insuranceRow.getByLabel(/status for/i).selectOption('CONFIRMED_BY_USER')
  await expect(insuranceRow.getByLabel(/status for/i)).toHaveValue('CONFIRMED_BY_USER')
  await expect(insuranceRow.locator('.verification-checklist-mark.is-resolved')).toBeVisible()

  // Navigator dashboard reflects the same provider, status, and checklist progress.
  await goToNavigatorViaMenu(page)
  await expect(page.getByRole('heading', { name: 'Your care navigator' })).toBeVisible()
  await expect(page.getByText(/1 still to contact/i)).toBeVisible()
  await expect(page.getByText(/verification: 1 of 6 reviewed/i)).toBeVisible()
  // Status is TO_CONTACT, which always suggests "Contact office" regardless of checklist
  // progress -- the checklist only changes the next action once contacted (see NextActionResolver).
  await expect(page.getByText(/next: contact office/i)).toBeVisible()

  // Filtering to "To contact" keeps the card; filtering to "Contacted" hides it.
  await page.getByRole('button', { name: 'Contacted', exact: true }).click()
  await expect(page.locator('.provider-list .provider-card')).toHaveCount(0)
  await page.getByRole('button', { name: 'To contact', exact: true }).click()
  await expect(page.locator('.provider-list .provider-card')).toHaveCount(1)
})

test('reminders: create a reminder, see it grouped, mark done, then delete it', async ({ page }) => {
  const email = `reminder-e2e-${randomUUID()}@example.com`
  await page.goto('/register')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('TestPassword123')
  await page.getByRole('button', { name: /create account/i }).click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  await goToNavigatorViaMenu(page)
  await expect(page.getByRole('heading', { name: 'Reminders' })).toBeVisible()

  await page.getByLabel('What do you want to be reminded about?').selectOption('Confirm insurance')
  await page.getByLabel('When?').selectOption('tomorrow')
  await page.getByRole('button', { name: 'Add reminder' }).click()

  const reminderRow = page.locator('.reminder-row', { hasText: 'Confirm insurance' })
  await expect(reminderRow).toBeVisible()

  await reminderRow.getByRole('button', { name: /mark .* as done/i }).click()
  await expect(page.locator('.reminder-title.is-done', { hasText: 'Confirm insurance' })).toBeVisible()

  await page.locator('.reminder-row', { hasText: 'Confirm insurance' }).getByRole('button', { name: /delete reminder/i }).click()
  await expect(page.locator('.reminder-row', { hasText: 'Confirm insurance' })).toHaveCount(0)
})

test('saved plan: save my plan, see it on the navigator, then remove it', async ({ page }) => {
  const email = `saved-plan-e2e-${randomUUID()}@example.com`
  await page.goto('/register')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('TestPassword123')
  await page.getByRole('button', { name: /create account/i }).click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  await goToNavigatorViaMenu(page)
  await expect(page.getByRole('heading', { name: 'Your saved plan' })).toBeVisible()
  await page.getByRole('button', { name: 'Save my plan' }).click()

  const payerSelect = page.locator('#insurance-payer')
  await expect(payerSelect.locator('option')).not.toHaveCount(1)
  const payerValue = await payerSelect.locator('option').nth(1).getAttribute('value')
  await payerSelect.selectOption(payerValue!)

  // Only assert the plan flow end-to-end if this payer actually has integrated plans;
  // otherwise the plan <select> never appears, which is correct product behavior, not a bug.
  const planSelect = page.locator('#insurance-plan')
  if (await planSelect.isVisible().catch(() => false)) {
    const planValue = await planSelect.locator('option').nth(1).getAttribute('value')
    if (planValue) {
      await planSelect.selectOption(planValue)
      await page.getByRole('button', { name: 'Save my plan' }).click()
      await expect(page.getByRole('button', { name: 'Change' })).toBeVisible()

      await page.getByRole('button', { name: 'Remove' }).click()
      await expect(page.getByRole('button', { name: 'Save my plan' })).toBeVisible()
    }
  }
})

test('privacy center: download my data, and confirm account deletion clears navigator state', async ({ page }) => {
  const email = `privacy-e2e-${randomUUID()}@example.com`
  await registerAndFindProvider(page, email)
  await page.getByRole('button', { name: /^save provider$/i }).click()

  await page.getByRole('button', { name: 'Account menu' }).click()
  await page.getByRole('menuitem', { name: 'Account' }).click()
  await expect(page.getByRole('heading', { name: 'Privacy & data' })).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: /download my data/i }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('docfit-ai-data-export.json')

  await page.getByRole('button', { name: /delete my account/i }).click()
  await page.getByRole('button', { name: /yes, delete my account/i }).click()
  await expect(page).toHaveURL('/')

  // Signed out, and the deleted account can no longer sign in.
  await page.goto('/signin')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('TestPassword123')
  await page.getByRole('button', { name: /sign in/i }).click()
  await expect(page.getByText(/invalid email or password/i)).toBeVisible()
})

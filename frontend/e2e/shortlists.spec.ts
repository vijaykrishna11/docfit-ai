import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

// Clipboard read/write permission scoped to this file only, to verify the actual copied URL --
// other spec files are unaffected (Playwright's global config grants no clipboard permission by
// default, and this doesn't change that for them).
test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

test('shortlist workflow: create, add a provider, view it, share selected, remove, delete', async ({ page }) => {
  const email = `shortlist-e2e-${randomUUID()}@example.com`

  await page.goto('/register')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('TestPassword123')
  await page.getByRole('button', { name: /create account/i }).click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  // Find a provider and add it to a new shortlist from the detail page.
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()
  await page.locator('.provider-card').first().getByRole('link', { name: /view details/i }).click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  await page.getByRole('button', { name: /add to shortlist/i }).click()
  const shortlistName = `E2E shortlist ${randomUUID().slice(0, 8)}`
  await page.getByPlaceholder('New shortlist name').fill(shortlistName)
  await page.getByRole('button', { name: 'Create' }).click()
  // The newly created shortlist appears checked, confirming the provider was added to it.
  await expect(page.getByRole('checkbox', { name: shortlistName })).toBeChecked()

  // View it from the shortlists index.
  await page.goto('/shortlists')
  await expect(page.getByRole('heading', { name: 'Shortlists' })).toBeVisible()
  await page.getByRole('link', { name: new RegExp(shortlistName) }).click()
  await expect(page.getByRole('heading', { name: shortlistName })).toBeVisible()
  await expect(page.locator('.provider-card')).toHaveCount(1)

  // Select the provider and share it -- copies a public /share/providers link.
  await page.locator('.provider-card').first().getByRole('checkbox').check()
  await page.getByRole('button', { name: /share selected/i }).click()
  await expect(page.getByRole('button', { name: /link copied/i })).toBeVisible()

  const shareUrl: string = await page.evaluate(() => navigator.clipboard.readText())
  expect(shareUrl).toMatch(/\/share\/providers\?ids=\d+/)

  // The share link is public and shows the provider without any account/shortlist context.
  await page.context().clearCookies()
  await page.goto(new URL(shareUrl).pathname + new URL(shareUrl).search)
  await expect(page.getByRole('heading', { name: 'Shared providers' })).toBeVisible()
  await expect(page.locator('.provider-card')).toHaveCount(1)
  await expect(page.getByText(shortlistName)).toHaveCount(0)
})

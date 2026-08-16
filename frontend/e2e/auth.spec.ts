import { expect, test } from '@playwright/test'

// Assumes a real local backend + Postgres with the demo NPPES import already run -- see
// docs/e2e-testing.md. Each run registers a fresh, uniquely-timestamped account -- it never
// reuses or depends on pre-existing account data.

test('register, save a provider, sign out, then sign back in and see it saved', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`
  const password = 'e2e-test-password-1'

  await page.goto('/register')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: /create account/i }).click()

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  // Search and save the first provider.
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()

  const firstCard = page.locator('.provider-card').first()
  await expect(firstCard).toBeVisible({ timeout: 15000 })
  await firstCard.getByRole('button', { name: /save this provider/i }).click()
  await expect(firstCard.getByRole('button', { name: /remove from saved providers/i })).toBeVisible()

  // Sign out via the account menu.
  await page.locator('.account-menu-trigger').click()
  await page.getByRole('menuitem', { name: /sign out/i }).click()

  await expect(page.getByRole('link', { name: /sign in/i })).toBeVisible()

  // Sign back in and confirm the saved provider persisted server-side.
  await page.getByRole('link', { name: /sign in/i }).click()
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: /sign in/i }).click()

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await page.locator('.account-menu-trigger').click()
  await page.getByRole('menuitem', { name: /saved providers/i }).click()

  await expect(page.locator('.provider-card').first()).toBeVisible({ timeout: 15000 })
})

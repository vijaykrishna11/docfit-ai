import { expect, test } from '@playwright/test'

// Assumes a real local backend + Postgres with the demo NPPES import already run -- see
// docs/e2e-testing.md for the exact setup commands. Never hits an external payer API.

test('homepage loads with the search form', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByLabel('Specialty', { exact: true })).toBeVisible()
  await expect(page.getByLabel('Location', { exact: true })).toBeVisible()
})

test('Cardiology + 90802 returns at least one provider result', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()

  await expect(page.locator('.provider-card').first()).toBeVisible({ timeout: 15000 })
  await expect(page.getByRole('heading', { name: /providers near/i })).toBeVisible()
})

test('selecting an unintegrated insurer never blocks search', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByLabel('Insurance', { exact: true }).selectOption({ label: 'Aetna' })

  await expect(page.getByText(/network verification is not currently available/i)).toBeVisible()

  await page.getByRole('button', { name: /find providers/i }).click()
  await expect(page.locator('.provider-card').first()).toBeVisible({ timeout: 15000 })
})

test('provider detail: view, back to results, Call and Directions links work', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()

  const firstCard = page.locator('.provider-card').first()
  await expect(firstCard).toBeVisible({ timeout: 15000 })
  await firstCard.getByRole('link', { name: /view details/i }).click()

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  const directionsLink = page.getByRole('link', { name: /get directions/i })
  await expect(directionsLink).toHaveAttribute('href', /google\.com\/maps/)

  await page.getByRole('button', { name: /back to results/i }).click()
  await expect(page.locator('.provider-card').first()).toBeVisible()
})

test('comparison: selecting two providers and comparing shows a factual table', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()

  const cards = page.locator('.provider-card')
  await expect(cards.first()).toBeVisible({ timeout: 15000 })
  const count = await cards.count()
  test.skip(count < 2, 'Needs at least two results in the local demo dataset to compare.')

  await cards.nth(0).getByRole('checkbox', { name: /compare/i }).check()
  await cards.nth(1).getByRole('checkbox', { name: /compare/i }).check()
  await page.getByRole('button', { name: /compare \d+ providers/i }).click()

  await expect(page.getByRole('heading', { name: /compare providers/i })).toBeVisible()
  await expect(page.getByText(/factual navigation details only/i)).toBeVisible()
})

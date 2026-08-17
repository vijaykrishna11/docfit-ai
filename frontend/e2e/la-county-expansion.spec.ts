import { expect, test } from '@playwright/test'

// Exercises the LA County Expansion V5.1 additions against the real backend/Postgres with the
// real bounded LA County import already applied (5,854 providers across 295 loaded ZIPs, 30
// directly queried -- see docs/la-county-provider-import.md). Never hits an external payer API.

test('location suggestions dedupe a city across its many real ZIPs', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Location', { exact: true }).fill('Long')

  // Long Beach spans a dozen+ real LA County ZIPs -- Location Suggestions V3 must show exactly
  // one "Long Beach, CA" suggestion, not one per ZIP.
  const options = page.locator('#location-suggestions option')
  await expect(options).toHaveCount(1, { timeout: 5000 })
  await expect(options.first()).toHaveAttribute('value', 'Long Beach, CA')
})

test('searching a real newly-imported LA County city (Pasadena) returns results', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Specialty', { exact: true }).selectOption('PRIMARY_CARE')
  await page.getByLabel('Location', { exact: true }).fill('Pasadena, CA')
  await page.getByRole('button', { name: /find providers/i }).click()

  await expect(page.locator('.provider-card').first()).toBeVisible({ timeout: 15000 })
})

test('all 19 specialty categories are present and selectable', async ({ page }) => {
  await page.goto('/')
  const options = page.locator('#specialty option:not([disabled])')
  await expect(options).toHaveCount(19)
})

test('searching an unsupported area shows an honest error, never silently searches elsewhere', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('00000')
  await page.getByRole('button', { name: /find providers/i }).click()

  await expect(page.getByText(/unknown zip code|unknown location/i)).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.provider-card')).toHaveCount(0)
})

test('a long real city name does not cause horizontal page overflow on a narrow viewport', async ({ page }) => {
  // "West Whittier-Los Nietos" is a real, long (24-char) LA County city name from the imported
  // geography -- a realistic stress case for narrow mobile layouts.
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/')
  await page.getByLabel('Location', { exact: true }).fill('West Whittier')
  await expect(page.locator('#location-suggestions option')).toHaveCount(1, { timeout: 5000 })

  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  const clientWidth = await page.evaluate(() => document.documentElement.clientWidth)
  expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1)
})

test('coverage panel reports real counts and distinguishes reference geography from imported provider data', async ({ page }) => {
  await page.goto('/')
  await page.getByText('Data sources & transparency').scrollIntoViewIfNeeded()

  await expect(page.getByText(/current docfit ai coverage/i)).toBeVisible()
  await expect(page.getByText(/reference geography/i)).toBeVisible()
  await expect(page.getByText(/not the same as having provider data for all of those areas/i)).toBeVisible()
})

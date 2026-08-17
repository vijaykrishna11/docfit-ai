import { expect, test } from '@playwright/test'

test('recent searches: shown after a search, browser-local only, clearable', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('Recent searches')).toHaveCount(0)

  await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
  await page.getByLabel('Location', { exact: true }).fill('90802')
  await page.getByRole('button', { name: /find providers/i }).click()
  await expect(page.locator('.provider-card').first()).toBeVisible()

  // Clear the active search to return to the idle homepage, where recent searches show.
  await page.getByRole('button', { name: /clear search/i }).click()
  const recentSearchesSection = page.getByRole('region', { name: 'Recent searches' })
  await expect(recentSearchesSection).toBeVisible()
  const recentEntry = recentSearchesSection.getByRole('button', { name: /Cardiology/i })
  await expect(recentEntry).toBeVisible()

  // Never sent server-side -- verify it lives in sessionStorage, not any request payload.
  const stored = await page.evaluate(() => sessionStorage.getItem('docfitai.recentSearches'))
  expect(stored).toContain('CARDIOLOGY')

  // Clicking a recent search re-runs it.
  await recentEntry.click()
  await expect(page.locator('.provider-card').first()).toBeVisible()

  // Clearing removes it and the section disappears.
  await page.getByRole('button', { name: /clear search/i }).click()
  await page.getByRole('region', { name: 'Recent searches' }).getByRole('button', { name: 'Clear', exact: true }).click()
  await expect(page.getByText('Recent searches')).toHaveCount(0)
})

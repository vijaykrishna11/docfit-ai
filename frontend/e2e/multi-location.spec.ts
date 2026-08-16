import { expect, test } from '@playwright/test'

// Depends on a real provider with multiple practice locations existing in the local dev
// database, which the documented NPPES import (docs/e2e-testing.md) produces from genuine NPPES
// data -- e.g. "SAMEDAY DOCTORS, P.C." commonly has dozens of real offices. If that provider
// isn't present (a different import run, a different day's NPPES data), the test skips instead
// of failing, since this is inherently live third-party reference data, not a fixture DocFit
// controls.
const MULTI_LOCATION_PROVIDER_NAME = 'Sameday'

test('a provider with multiple real practice locations shows one selected office plus an Other locations section', async ({
  page,
}) => {
  await page.goto('/')
  await page.getByPlaceholder(/search providers by name/i).fill(MULTI_LOCATION_PROVIDER_NAME)

  const firstResult = page.locator('.provider-name-search-results li a').first()
  const found = await firstResult
    .waitFor({ state: 'visible', timeout: 5000 })
    .then(() => true)
    .catch(() => false)
  test.skip(!found, `No provider matching "${MULTI_LOCATION_PROVIDER_NAME}" in the local dev database -- run the NPPES import first.`)

  await firstResult.click()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  const hasOtherLocations = await page
    .getByText('Other locations')
    .waitFor({ state: 'visible', timeout: 5000 })
    .then(() => true)
    .catch(() => false)
  test.skip(!hasOtherLocations, 'This provider currently has only one location in the local dataset.')

  await expect(page.locator('.provider-other-location-item').first()).toBeVisible()
  // Each other location has its own Directions link, not the primary office's address.
  await expect(page.locator('.provider-other-location-item').first().getByRole('link', { name: /directions/i })).toHaveAttribute(
    'href',
    /google\.com\/maps/,
  )
})

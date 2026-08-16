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

  // getByRole, not getByText: the "N practice locations" chip badge is also on the page and
  // would otherwise ambiguously substring-match the "Practice locations" heading text too.
  const hasOtherLocations = await page
    .getByRole('heading', { name: 'Practice locations' })
    .waitFor({ state: 'visible', timeout: 5000 })
    .then(() => true)
    .catch(() => false)
  test.skip(!hasOtherLocations, 'This provider currently has only one location in the local dataset.')

  const locationItems = page.locator('.location-switcher-item')
  await expect(locationItems.first()).toBeVisible()
  // Each office has its own Directions link, not the primary office's address.
  await expect(locationItems.first().getByRole('link', { name: /directions/i })).toHaveAttribute('href', /google\.com\/maps/)

  // Switching to a non-active office updates the page's main address/phone/directions -- not
  // just the switcher list -- to that office's own data (CLAUDE.md "Location Switcher"). Captured
  // by address text, not by a ":not(.is-active)" selector -- that selector's meaning changes the
  // moment the click makes this same element active, so re-querying it afterward would silently
  // resolve to a different (still-inactive) item instead of the one just clicked.
  const inactiveItem = page.locator('.location-switcher-item:not(.is-active)').first()
  const inactiveAddressText = (await inactiveItem.locator('.detail span').first().innerText()).split('\n')[0].trim()
  await inactiveItem.getByRole('button', { name: /use this location/i }).click()

  await expect(page.locator('.provider-detail-grid dd').first()).toContainText(inactiveAddressText)
  const nowActiveItem = page.locator('.location-switcher-item.is-active').filter({ hasText: inactiveAddressText })
  await expect(nowActiveItem).toHaveCount(1)
})

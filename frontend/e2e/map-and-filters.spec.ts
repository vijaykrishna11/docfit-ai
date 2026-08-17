import { expect, test } from '@playwright/test'

test.describe('discovery map and practical-fit filters', () => {
  test('map renders, a marker opens a popup and highlights its card, and a filter narrows via the URL', async ({ page }) => {
    await page.goto('/')
    await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
    await page.getByLabel('Location', { exact: true }).fill('90802')
    await page.getByRole('button', { name: /find providers/i }).click()

    const firstCard = page.locator('.provider-card').first()
    await expect(firstCard).toBeVisible()

    // Desktop viewport: map pane is already visible alongside the list, no toggle needed.
    // Leaflet applies its own "leaflet-container" class directly onto the ref'd div (same
    // element, not a child) once the map actually initializes.
    await expect(page.locator('.results-map.leaflet-container')).toBeVisible()

    const marker = page.locator('.leaflet-marker-icon').first()
    await expect(marker).toBeVisible()
    // Leaflet pans/zooms the map on load (fitBounds), which keeps markers in continuous CSS-transform
    // motion just long enough to defeat Playwright's default stability check on some runs -- force
    // is safe here since we assert the resulting popup content directly afterward.
    await marker.click({ force: true })

    await expect(page.locator('.map-popup')).toBeVisible()
    await expect(page.locator('.map-popup-name')).not.toBeEmpty()
    await expect(page.locator('.map-popup-specialty')).toContainText('Cardio')

    // Marker click also selects the matching result card (CLAUDE.md "Map Marker Interaction").
    await expect(page.locator('.provider-card.is-map-selected')).toHaveCount(1)

    // Practical-fit filter: applying one updates the URL and shows an active-filter chip, and the
    // open panel survives the resulting refining search (it must not be torn down mid-interaction).
    await page.getByRole('button', { name: /^Filters/ }).click()
    await expect(page.getByText('More precise location available')).toBeVisible()
    await page.getByLabel('Has phone number on file').click()
    await expect(page).toHaveURL(/hasPhone=true/)
    await expect(page.getByText('More precise location available')).toBeVisible()
    await page.getByRole('button', { name: 'Done' }).click()
    await expect(page.getByRole('button', { name: /Has phone/ })).toBeVisible()

    // Clearing the filter removes it from the URL and the chip.
    await page.getByRole('button', { name: /Has phone/ }).click()
    await expect(page).not.toHaveURL(/hasPhone=true/)
  })

  test('mobile viewport: map is opt-in via a List/Map toggle, never forced split-screen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/')
    await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
    await page.getByLabel('Location', { exact: true }).fill('90802')
    await page.getByRole('button', { name: /find providers/i }).click()

    await expect(page.locator('.provider-card').first()).toBeVisible()
    // List is the default view -- the map pane exists in the DOM (so it's still reachable
    // without extra requests once toggled) but is hidden, and the list is not.
    await expect(page.locator('.results-list-pane')).toBeVisible()
    await expect(page.locator('.results-map-pane')).toBeHidden()

    await page.getByRole('button', { name: 'Map', exact: true }).click()
    await expect(page.locator('.results-map-pane')).toBeVisible()
    await expect(page.locator('.results-list-pane')).toBeHidden()
  })
})

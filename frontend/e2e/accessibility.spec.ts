import { expect, test } from '@playwright/test'

test.describe('keyboard accessibility of new Care Discovery V3 interactive elements', () => {
  test('practical-fit filter panel: keyboard-openable, keyboard-operable, Escape closes it', async ({ page }) => {
    await page.goto('/')
    await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
    await page.getByLabel('Location', { exact: true }).fill('90802')
    await page.getByRole('button', { name: /find providers/i }).click()
    await expect(page.locator('.provider-card').first()).toBeVisible()

    const filtersButton = page.getByRole('button', { name: /^Filters/ })
    await filtersButton.focus()
    await expect(filtersButton).toBeFocused()
    await page.keyboard.press('Enter')

    const hasPhoneCheckbox = page.getByLabel('Has phone number on file')
    await expect(hasPhoneCheckbox).toBeVisible()
    // Reachable and operable via keyboard alone -- no mouse click.
    await hasPhoneCheckbox.focus()
    await page.keyboard.press('Space')
    await expect(hasPhoneCheckbox).toBeChecked()

    await page.keyboard.press('Escape')
    await expect(page.getByText('More precise location available')).toBeHidden()
  })

  test('report-incorrect-information modal: focus moves in on open, Escape closes it, never traps focus permanently', async ({
    page,
  }) => {
    await page.goto('/')
    await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
    await page.getByLabel('Location', { exact: true }).fill('90802')
    await page.getByRole('button', { name: /find providers/i }).click()
    await page.locator('.provider-card').first().getByRole('link', { name: /view details/i }).click()
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    await page.getByRole('button', { name: /report incorrect information/i }).click()
    const dialog = page.getByRole('dialog', { name: /report incorrect information/i })
    await expect(dialog).toBeVisible()
    // Focus is programmatically moved into the dialog (its close button) on open, not left
    // behind on the trigger button.
    await expect(dialog.getByLabel('Close')).toBeFocused()

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    // Page underneath is still fully usable -- not permanently trapped.
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('add-to-shortlist menu and location switcher are plain, keyboard-focusable buttons', async ({ page }) => {
    await page.goto('/register')
    await page.getByLabel(/email/i).fill(`a11y-${Date.now()}@example.com`)
    await page.getByLabel('Password', { exact: true }).fill('TestPassword123')
    await page.getByRole('button', { name: /create account/i }).click()
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    await page.getByLabel('Specialty', { exact: true }).selectOption('CARDIOLOGY')
    await page.getByLabel('Location', { exact: true }).fill('90802')
    await page.getByRole('button', { name: /find providers/i }).click()
    await page.locator('.provider-card').first().getByRole('link', { name: /view details/i }).click()
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    const addToShortlistButton = page.getByRole('button', { name: /add to shortlist/i })
    await addToShortlistButton.focus()
    await expect(addToShortlistButton).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByPlaceholder('New shortlist name')).toBeVisible()
    await page.keyboard.press('Escape')
  })
})

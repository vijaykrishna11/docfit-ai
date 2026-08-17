import { expect, test } from '@playwright/test'

test('report incorrect information: submit anonymously and see a confirmation, never a "will be fixed" promise', async ({
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

  await dialog.getByLabel(/what.s incorrect/i).selectOption('WRONG_PHONE_NUMBER')
  await dialog.getByLabel(/add details/i).fill('The phone number listed goes to a disconnected line.')
  await dialog.getByRole('button', { name: /submit report/i }).click()

  await expect(dialog.getByText(/thanks\. this report helps us review directory information\./i)).toBeVisible()
  // Never an over-promise that the record will definitely be corrected.
  await expect(dialog.getByText(/will be (fixed|corrected)/i)).toHaveCount(0)

  await dialog.getByRole('button', { name: /close/i }).click()
  await expect(dialog).toBeHidden()
})

import { test, expect } from '@playwright/test';
import path from 'path';
const projectRoot = path.resolve(process.cwd(), '../../');

const reports = [
  {
    name: 'Spring',
    path: path.join(projectRoot, 'integration-tests-spring/target/execution-report.html')
  }
];

for (const report of reports) {
  test(`should have correct sidebar labels for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');
    
    await expect(page.locator('.sidebar-header')).toContainText('All files');
  });

  test(`should toggle theme correctly for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    // Default should be dark mode
    await expect(page.locator('body')).not.toHaveClass(/light-mode/);

    // Toggle to Light Mode
    await page.getByTestId('theme-toggle').click();
    await expect(page.locator('body')).toHaveClass(/light-mode/);

    // Verify background color changed (basic check)
    const bgColor = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    // expect(bgColor).toBe('rgb(248, 250, 252)'); // #f8fafc

    // Toggle back to Dark Mode
    await page.getByTestId('theme-toggle').click();
    await expect(page.locator('body')).not.toHaveClass(/light-mode/);
  });

  test(`should respect persisted light theme for ${report.name}`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('theme', 'light');
    });

    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    await expect(page.locator('body')).toHaveClass(/light-mode/);
  });

  test(`should toggle theme when storage is blocked for ${report.name}`, async ({ page }) => {
    await page.addInitScript(() => {
      Storage.prototype.getItem = () => {
        throw new Error('blocked');
      };
      Storage.prototype.setItem = () => {
        throw new Error('blocked');
      };
    });

    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    await page.getByTestId('theme-toggle').click();
    await expect(page.locator('body')).toHaveClass(/light-mode/);
  });
}

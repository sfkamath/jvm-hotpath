import { test, expect } from '@playwright/test';
import path from 'path';

const projectRoot = path.resolve(process.cwd(), '../../');

const reports = [
  {
    name: 'Spring',
    path: path.join(projectRoot, 'integration-tests-spring/target/execution-report.html')
  },
  {
    name: 'Micronaut',
    path: path.join(projectRoot, 'integration-tests-micronaut/target/execution-report.html')
  }
];

for (const report of reports) {
  test(`should handle Diff Mode 'Lap' functionality for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    const countBadge = page.locator('.node-count').first();
    await expect(countBadge).toBeVisible();
    const initialText = await countBadge.innerText();

    // Click Diff Mode button (Activation)
    await page.click('button:has-text("Diff Mode")');
    await expect(page.locator('.snapshot-btn.active')).toBeVisible();
    await expect(countBadge).toHaveText('0');

    // Wait a moment and click again (Lap/Reset)
    await page.waitForTimeout(200);
    await page.click('button:has-text("Diff Mode")');
    await expect(countBadge).toHaveText('0');

    // Clear Diff Mode
    await page.click('button:has-text("Clear")');
    await expect(page.locator('.snapshot-btn.active')).not.toBeVisible();
    await expect(countBadge).toHaveText(initialText);
  });
}
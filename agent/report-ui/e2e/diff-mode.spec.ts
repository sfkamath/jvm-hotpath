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
  test(`should handle Diff Mode 'Reset' functionality for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    const countBadge = page.getByTestId('node-count').first();
    await expect(countBadge).toBeVisible();
    const initialText = await countBadge.innerText();

    // Click Diff Mode button (Activation)
    await page.getByTestId('diff-mode-toggle').click({ timeout: 10000 });
    
    // Check for active state on the group
    await expect(page.locator('[data-testid="diff-mode-group"].active')).toBeVisible({ timeout: 10000 });
    
    // In our static test report, hits - baseline hits = 0
    await expect(countBadge).toHaveText('0', { timeout: 10000 });

    // Wait a moment and click again (Reset/Re-zero)
    await page.waitForTimeout(500);
    console.log("Triggering Reset (Lap)...");
    await page.getByTestId('diff-mode-toggle').click({ timeout: 10000 });
    
    // Should still be active and still 0 (since it just re-zeroed)
    await expect(page.locator('[data-testid="diff-mode-group"].active')).toBeVisible();
    await expect(countBadge).toHaveText('0');

    // Exit Diff Mode using the 'X' button
    await page.getByTestId('diff-mode-clear').click();
    
    // Counts should be back to absolute
    await expect(page.locator('[data-testid="diff-mode-group"].active')).not.toBeVisible();
    await expect(countBadge).toHaveText(initialText);
  });

  test(`should have the 'All files' label in sidebar for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');
    
    await expect(page.locator('.sidebar-header')).toContainText('All files');
  });
}

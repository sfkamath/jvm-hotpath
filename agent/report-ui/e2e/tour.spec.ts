import { test, expect } from '@playwright/test';
import path from 'path';

const projectRoot = path.resolve(process.cwd(), '../../');
const reportPath = path.join(projectRoot, 'integration-tests-spring/target/execution-report.html');

test('Feature Tour should start successfully', async ({ page }) => {
  const fileUrl = `file://${reportPath}`;
  await page.goto(fileUrl);
  await page.waitForSelector('#app');

  // Click Help button to start tour
  await page.click('button:has-text("Help")');

  // Check if driver.js popover appears
  const popover = page.locator('.driver-popover');
  await expect(popover).toBeVisible();
  
  // Verify first step content (Counts)
  await expect(popover.locator('.driver-popover-title')).toHaveText('The Hotpath X-Ray');
  
  // Advance tour
  await page.waitForTimeout(500);
  await page.click('.driver-popover-next-btn', { force: true });
  
  // Verify second step (Gutter)
  await expect(popover.locator('.driver-popover-title')).toHaveText('Line-Level Intensity');

  // Close tour
  await page.keyboard.press('Escape');
  await expect(popover).not.toBeVisible();
});

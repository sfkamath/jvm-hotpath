import { test, expect } from '@playwright/test';
import path from 'path';

const projectRoot = path.resolve(process.cwd(), '../../');
const reports = [
  {
    name: 'Spring',
    path: path.join(projectRoot, 'integration-tests-spring/target/site/jvm-hotpath/execution-report.html')
  },
  {
    name: 'Micronaut',
    path: path.join(projectRoot, 'integration-tests-micronaut/target/site/jvm-hotpath/execution-report.html')
  }
];

for (const report of reports) {
  test(`Feature Tour should start successfully for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    // Click Help button to start tour
    await page.getByTestId('tour-toggle').click();

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

  test(`Feature Tour highlights counts for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    await page.getByTestId('tour-toggle').click();
    
    // Wait for the tour to actually start (account for the setTimeout buffer)
    const popover = page.locator('.driver-popover');
    await expect(popover).toBeVisible();

    const activeTreeCount = page.locator('.driver-active-element.node-count, .driver-active-element.sidebar-header');
    expect(await activeTreeCount.count()).toBeGreaterThan(0);

    await page.click('.driver-popover-next-btn', { force: true });
    const activeGutter = page.locator(
      '.driver-active-element[data-testid="gutter-count"], .driver-active-element.gutter'
    );
    expect(await activeGutter.count()).toBeGreaterThan(0);

    // Advance through the rest of the steps
    await page.click('.driver-popover-next-btn', { force: true }); // Sidebar
    await page.click('.driver-popover-next-btn', { force: true }); // Live status
    await page.click('.driver-popover-next-btn', { force: true }); // Diff Mode
    
    // Verify the new Heatmap step
    await page.click('.driver-popover-next-btn', { force: true });
    await expect(popover.locator('.driver-popover-title')).toHaveText('Global Heatmap');
    const heatmapLegend = page.locator('.driver-active-element.heatmap-legend');
    expect(await heatmapLegend.count()).toBeGreaterThan(0);

    await page.keyboard.press('Escape');
  });
}

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
  test(`should show execution counts in the file tree for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    const counts = page.locator('.node-count');
    await expect(counts.first()).toBeVisible();

    const countText = await counts.first().innerText();
    expect(parseInt(countText.replace(/[^0-9]/g, ''), 10)).toBeGreaterThan(0);
  });

  test(`should show execution counts in the code gutter for ${report.name}`, async ({ page }) => {
    const fileUrl = `file://${report.path}`;
    await page.goto(fileUrl);
    await page.waitForSelector('#app');

    // Expand all folders
    const chevrons = page.locator('.chevron:text-is("▸")');
    while (await chevrons.count() > 0) {
      await chevrons.first().click();
      await page.waitForTimeout(100);
    }

    const hotFiles = page.locator('.tree-node-wrapper:not(.is-folder) .tree-item').filter({ has: page.locator('.node-count') });
    const count = await hotFiles.count();
    
    let foundGutter = false;
    for (let i = 0; i < count; i++) {
      const file = hotFiles.nth(i);
      await file.click();
      await page.waitForTimeout(300);
      
      const gutterCounts = page.locator('.gutter .cnt');
      if (await gutterCounts.count() > 0) {
        foundGutter = true;
        break;
      }
    }
    expect(foundGutter).toBe(true);
  });
}

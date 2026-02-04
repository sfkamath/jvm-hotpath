import { test, expect, type Page } from '@playwright/test';
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

const openReport = async (page: Page, reportPath: string) => {
  await page.addInitScript(() => localStorage.clear());
  await page.goto(`file://${reportPath}`);
  await page.waitForSelector('#app');
};

for (const report of reports) {
  test.describe(`${report.name} tree behavior`, () => {
    test('aggregates toggle hides folder counts but keeps folders', async ({ page }) => {
      await openReport(page, report.path);

      const folders = page.getByTestId('tree-folder');
      await expect(folders.first()).toBeVisible();

      const folderCounts = page.locator(
        '.tree-node-wrapper.is-folder > .tree-item [data-testid="node-count"]'
      );
      expect(await folderCounts.count()).toBeGreaterThan(0);

      await page.locator('.sidebar-header').scrollIntoViewIfNeeded();
      await page.evaluate(() => {
        const input = document.querySelector(
          '[data-testid="aggregates-toggle"]'
        ) as HTMLInputElement | null;
        if (input) {
          input.checked = false;
          input.dispatchEvent(new Event('change', { bubbles: true }));
        }
      });

      await expect(folderCounts).toHaveCount(0);
      await expect(folders.first()).toBeVisible();

      const fileCounts = page.locator('.tree-node-wrapper:not(.is-folder) [data-testid="node-count"]');
      expect(await fileCounts.count()).toBeGreaterThan(0);
    });

    test('folders are expanded by default', async ({ page }) => {
      await openReport(page, report.path);

      const openFolders = page.locator('.tree-node-wrapper.is-folder.is-open');
      expect(await openFolders.count()).toBeGreaterThan(0);
    });

    test('all files toggle persists to storage', async ({ page }) => {
      await openReport(page, report.path);

      await page.locator('.sidebar-header').scrollIntoViewIfNeeded();
      await page.locator('label[title="Show all source files (including 0 hotpath)"]').click();

      const stored = await page.evaluate(() => localStorage.getItem('showAllSources'));
      expect(stored).toBe('true');
    });
  });
}

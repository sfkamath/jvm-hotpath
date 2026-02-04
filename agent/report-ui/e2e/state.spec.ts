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

const expandAllFolders = async (page: Page) => {
  const chevrons = page.getByTestId('tree-chevron').filter({ hasText: '▸' });
  while (await chevrons.count() > 0) {
    await chevrons.first().click();
    await page.waitForTimeout(100);
  }
};

for (const report of reports) {
  test.describe(`${report.name} state`, () => {
    test('selecting a file updates toolbar and URL hash', async ({ page }) => {
      await openReport(page, report.path);
      await expandAllFolders(page);

      const file = page.getByTestId('tree-file').first();
      const fileName = (await file.getByTestId('node-name').innerText()).trim();

      await file.click();
      await expect(page.locator('.toolbar-left')).toContainText(fileName);

      const decodedHash = await page.evaluate(() =>
        decodeURIComponent(window.location.hash.substring(1))
      );
      expect(decodedHash).toContain(fileName);
    });

    test('aggregates toggle persists to storage', async ({ page }) => {
      await openReport(page, report.path);
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

      const stored = await page.evaluate(() => localStorage.getItem('showAggregates'));
      expect(stored).toBe('false');
    });

    test('collapsing a folder stores its path', async ({ page }) => {
      await openReport(page, report.path);
      await expandAllFolders(page);

      const chevron = page.getByTestId('tree-chevron').first();
      await chevron.click();

      const stored = await page.evaluate(() => {
        const raw = localStorage.getItem('collapsedFolders');
        if (!raw) return [];
        try {
          return JSON.parse(raw);
        } catch {
          return [];
        }
      });

      expect(Array.isArray(stored)).toBe(true);
      expect(stored.length).toBeGreaterThan(0);
    });
  });
}

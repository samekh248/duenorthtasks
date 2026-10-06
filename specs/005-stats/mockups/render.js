// Renders the mockup HTML files in this folder to PNG with the preinstalled Playwright Chromium.
// Usage (from this folder): NODE_PATH=$(npm root -g) node render.js [name ...]   (default: stats-mockups)
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const names = process.argv.slice(2).length ? process.argv.slice(2) : ['stats-mockups'];
  const browser = await chromium.launch();
  for (const name of names) {
    const page = await browser.newPage({ viewport: { width: 2122, height: 1200 } });
    await page.goto('file://' + path.join(__dirname, name + '.html'));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(__dirname, name + '.png'), fullPage: true });
  }
  await browser.close();
})();

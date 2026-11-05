import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import {
  addProduct, api, baseUrl, catalog, category, checkout, cleanup, clickText,
  keyword, launch, observedGet, openAccount, productIds, report, saveFailure, search, seedProducts
} from './support.mjs';

describe('Real marketplace browser flows', { concurrency: false, timeout: 300000 }, () => {
  let browser, client, seller, otherSeller, stranger, delivered;
  before(async () => {
    browser = await launch();
    seller = await openAccount(browser, 'SELLER', 'Seller');
    otherSeller = await openAccount(browser, 'SELLER', 'OtherSeller');
    client = await openAccount(browser, 'CLIENT', 'Client');
    stranger = await openAccount(browser, 'CLIENT', 'Stranger');
    seedProducts(seller.user.id, otherSeller.user.id);
  });
  after(async () => {
    try { if (browser) await browser.close(); } finally { cleanup(); }
  });

  function scenario(name, run) {
    it(name, { timeout: 90000 }, async () => {
      try { await run(); } catch (error) {
        if (browser) await saveFailure(browser, name);
        throw error;
      }
    });
  }

  scenario('search combines filters, sorts, paginates and handles invalid or empty results', async () => {
    const page = client.page;
    const initial = await catalog(page);
    assert.equal(initial.totalElements, 14);
    assert.equal(initial.totalPages, 2);
    const next = await search(page, { q: keyword, page: 1 }, () => clickText(page, 'nav[aria-label="Product pages"] button', 'Next'));
    assert.equal(next.content.length, 2);
    const filtered = await search(page, { q: keyword, category, page: 0 }, () => page.locator('#categoryFilter').fill(category));
    assert.equal(filtered.totalElements, 13, 'Changing filters resets pagination');
    async function slide(id, value) {
      await page.$eval(id, (input, price) => {
        input.value = String(price);
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }, value);
    }
    await search(page, { category, minPrice: 10 }, () => slide('#minPriceSlider', 10));
    const prices = await search(page, { category, maxPrice: 12 }, () => slide('#maxPriceSlider', 12));
    assert.equal(prices.totalElements, 2);
    const sorted = await search(page, { category, maxPrice: 12, sort: 'price-desc' }, () => page.select('#sortSelect', 'price-desc'));
    assert.deepEqual(sorted.content.map(product => product.price), [12, 10]);
    await page.locator('#minPriceFilter').fill('25');
    await page.waitForFunction(() => document.querySelector('#priceFilterError')?.textContent.includes('Minimum'));
    assert.equal(await page.$eval('form button[type="submit"]', button => button.disabled), true);
    await page.goto(`${baseUrl}/products`, { waitUntil: 'networkidle0' });
    await catalog(page);
    const inStock = await search(page, { q: keyword, inStock: true }, async () => {
      const value = await page.$eval('select[aria-label="Filter inventory availability"]', element => element.options[1].value);
      await page.select('select[aria-label="Filter inventory availability"]', value);
    });
    assert.equal(inStock.totalElements, 13);
    const empty = await search(page, { q: `${keyword}missing` }, () => page.locator('input[aria-label="Search products"]').fill(`${keyword}missing`));
    assert.equal(empty.totalElements, 0);
    await page.waitForFunction(() => document.body.textContent.includes('No matching pieces found'));
  });

  scenario('a customer checks out a mixed-seller cart and pending orders contribute zero analytics', async () => {
    await catalog(client.page);
    await addProduct(client.page, productIds[0]);
    await addProduct(client.page, productIds[1]);
    await client.page.goto(`${baseUrl}/cart`, { waitUntil: 'networkidle0' });
    const quantity = client.page.waitForResponse(r => new URL(r.url()).pathname === `/api/cart/items/${productIds[0]}` && r.request().method() === 'PUT');
    await client.page.locator(`button[aria-label="Increase quantity of ${keyword} Chair 01"]`).click();
    assert.equal((await quantity).status(), 200);
    delivered = await checkout(client.page);
    assert.equal(delivered.totalAmount, 31);
    assert.equal(delivered.totalItems, 3);
    await report(client.page, 'CLIENT', { amount: 0, orders: 0, units: 0 });
    await report(seller.page, 'SELLER', { amount: 0, orders: 0, units: 0 }, '/seller/dashboard');
  });

  async function advanceSeller(page, id) {
    await page.goto(`${baseUrl}/orders/${id}`, { waitUntil: 'networkidle0' });
    for (const status of ['CONFIRMED', 'SHIPPED', 'DELIVERED']) {
      await page.select('#next-status', status);
      const pending = page.waitForResponse(r => r.url().endsWith(`/orders/${id}/status`) && r.request().method() === 'PATCH');
      await clickText(page, 'button[type="submit"]', 'Update status');
      const response = await pending;
      assert.equal(response.status(), 200);
      const stored = await api(page, `/orders/${id}`);
      assert.equal(stored.status, 200);
      assert.equal(stored.body.status, status);
      await page.waitForFunction(label => document.querySelector('main header')?.textContent.includes(label), {},
        status[0] + status.slice(1).toLowerCase());
      await page.waitForFunction(() => !document.querySelector('#next-status')?.disabled);
    }
  }

  scenario('seller status changes produce customer totals and isolate each seller in a mixed order', async () => {
    assert.ok(delivered, 'Checkout scenario must produce an order');
    await advanceSeller(seller.page, delivered.id);
    const partial = await api(client.page, `/orders/${delivered.id}`);
    assert.equal(partial.body.status, 'IN_PROGRESS');
    assert.deepEqual(partial.body.fulfillments.map(part => part.status), ['DELIVERED', 'PENDING']);
    await client.page.goto(`${baseUrl}/orders/${delivered.id}`, { waitUntil: 'networkidle0' });
    assert.match(await client.page.$eval('main header', el => el.textContent), /In progress/);
    assert.match(await client.page.$eval('tbody', el => el.textContent), /Delivered/);
    assert.match(await client.page.$eval('tbody', el => el.textContent), /Pending/);
    await report(client.page, 'CLIENT', { amount: 20, orders: 1, units: 2 });
    await report(otherSeller.page, 'SELLER', { amount: 0, orders: 0, units: 0 });
    await advanceSeller(otherSeller.page, delivered.id);
    assert.equal((await api(client.page, `/orders/${delivered.id}`)).body.status, 'DELIVERED');
    const customer = await report(client.page, 'CLIENT', { amount: 31, orders: 1, units: 3 });
    assert.equal(customer.products.length, 2);
    assert.equal(customer.months.reduce((total, month) => total + month.amount, 0), 31);
    const first = await report(seller.page, 'SELLER', { amount: 20, orders: 1, units: 2 }, '/seller/dashboard');
    assert.deepEqual(first.products.map(product => product.productId), [productIds[0]]);
    const second = await report(otherSeller.page, 'SELLER', { amount: 11, orders: 1, units: 1 });
    assert.deepEqual(second.products.map(product => product.productId), [productIds[1]]);
    await report(stranger.page, 'CLIENT', { amount: 0, orders: 0, units: 0 });
    assert.equal((await api(client.page, '/orders/analytics/seller')).status, 403);
    assert.equal((await api(seller.page, '/orders/analytics/me')).status, 403);
    assert.equal((await api(stranger.page, `/orders/${delivered.id}`)).status, 403);
    const scoped = await api(stranger.page, `/orders/analytics/me?userId=${client.user.id}`);
    assert.equal(scoped.status, 200);
    assert.equal(scoped.body.orderCount, 0, 'A caller-supplied identity cannot select another customer');
  });

  scenario('cancelling a second purchase leaves delivered analytics unchanged', async () => {
    await catalog(client.page);
    await addProduct(client.page, productIds[2]);
    const cancelled = await checkout(client.page);
    await client.page.goto(`${baseUrl}/orders/${cancelled.id}`, { waitUntil: 'networkidle0' });
    await clickText(client.page, 'button', 'Cancel order');
    const pending = client.page.waitForResponse(r => r.url().endsWith(`/orders/${cancelled.id}/cancel`) && r.request().method() === 'PATCH');
    await clickText(client.page, 'button', 'Yes, cancel it');
    const response = await pending;
    assert.equal(response.status(), 200);
    const stored = await api(client.page, `/orders/${cancelled.id}`);
    assert.equal(stored.status, 200);
    assert.equal(stored.body.status, 'CANCELLED');
    await report(client.page, 'CLIENT', { amount: 31, orders: 1, units: 3 });
    await report(seller.page, 'SELLER', { amount: 20, orders: 1, units: 2 });
  });

  scenario('date filters include the placement day, exclude other days, and validate ranges on mobile', async () => {
    const page = client.page;
    await report(page, 'CLIENT', { amount: 31, orders: 1, units: 3 });
    const placed = delivered.createdAt.slice(0, 10);
    const previous = new Date(`${placed}T00:00:00Z`);
    previous.setUTCDate(previous.getUTCDate() - 1);
    const yesterday = previous.toISOString().slice(0, 10);
    async function dates(from, to) {
      await page.evaluate((start, end) => {
        for (const [field, value] of [['from', start], ['to', end]]) {
          const input = document.querySelector(`app-analytics-dashboard input[formcontrolname="${field}"]`);
          input.value = value;
          input.dispatchEvent(new Event('input', { bubbles: true }));
        }
      }, from, to);
    }
    async function apply(from, to) {
      await dates(from, to);
      const pending = page.waitForResponse(r => {
        const url = new URL(r.url());
        return url.pathname.endsWith('/orders/analytics/me') && url.searchParams.get('from') === from && url.searchParams.get('to') === to;
      });
      await clickText(page, 'app-analytics-dashboard button', 'Apply dates');
      const response = await pending;
      assert.equal(response.status(), 200);
      const result = await observedGet(page, response);
      await page.waitForFunction(amount => document.querySelector('app-analytics-dashboard dl dd')?.textContent.trim() === `$${amount.toFixed(2)}`, {}, result.totalAmount);
      return result;
    }
    await page.setViewport({ width: 390, height: 844 });
    assert.equal((await apply(placed, placed)).totalAmount, 31);
    assert.equal((await apply(yesterday, yesterday)).totalAmount, 0);
    await page.waitForFunction(() => document.querySelector('app-analytics-dashboard')?.textContent.includes('No delivered orders'));
    await dates(placed, yesterday);
    await page.waitForFunction(() => document.querySelector('app-analytics-dashboard form button')?.disabled);
    assert.match(await page.$eval('app-analytics-dashboard [role="alert"]', node => node.textContent), /on or after/);
    assert.equal((await apply(placed, placed)).totalAmount, 31);
  });
  scenario('archive and restore are personal and preserve delivered analytics', async () => {
    async function archiveFromDetails(account, archived) {
      const page = account.page;
      await page.goto(`${baseUrl}/orders/${delivered.id}`, { waitUntil: 'networkidle0' });
      if (archived) await clickText(page, 'button', 'Archive order');
      const pending = page.waitForResponse(r => r.url().endsWith(`/orders/${delivered.id}/archive`) && r.request().method() === 'PATCH');
      await clickText(page, 'button', archived ? 'Yes, archive it' : 'Restore order');
      assert.equal((await pending).status(), 200);
      await page.waitForFunction(isArchived => {
        const text = document.querySelector('section[aria-label="Order visibility"]')?.textContent;
        return isArchived ? text?.includes('This order is archived') : text?.includes('Archive order');
      }, {}, archived);
      const stored = await api(page, `/orders/${delivered.id}`);
      assert.equal(stored.body.archived, archived);
    }
    async function list(account, archived, expected) {
      const page = account.page;
      await page.goto(`${baseUrl}/${account === client ? 'orders' : 'seller/orders'}`, { waitUntil: 'networkidle0' });
      if (archived) {
        await page.select('#order-visibility', 'true');
        const pending = page.waitForResponse(r => r.url().includes('/orders/') && new URL(r.url()).searchParams.get('archived') === 'true');
        await clickText(page, 'button[type="submit"]', 'Apply');
        assert.equal((await pending).status(), 200);
      }
      await page.waitForFunction((id, count) => document.querySelectorAll(`article a[href="/orders/${id}"]`).length === count, {}, delivered.id, expected);
    }
    await archiveFromDetails(client, true);
    await list(client, false, 0);
    await list(client, true, 1);
    await list(seller, false, 1);
    await archiveFromDetails(seller, true);
    await list(seller, false, 0);
    await list(seller, true, 1);
    await list(otherSeller, false, 1);
    await report(client.page, 'CLIENT', { amount: 31, orders: 1, units: 3 });
    await report(seller.page, 'SELLER', { amount: 20, orders: 1, units: 2 });
    assert.equal((await api(stranger.page, `/orders/${delivered.id}/archive`, 'PATCH', { archived: true })).status, 403);
    await archiveFromDetails(client, false);
    await list(client, false, 1);
    await list(client, true, 0);
    assert.equal((await api(seller.page, `/orders/${delivered.id}`)).body.archived, true);
    await archiveFromDetails(seller, false);
  });

  scenario('gateway and service failures share codes, messages and safe field details', async () => {
    function contract(result, status, code) {
      assert.equal(result.status, status);
      assert.equal(result.body.code, code);
      assert.equal(typeof result.body.message, 'string');
      assert.ok(result.body.details && typeof result.body.details === 'object' && !Array.isArray(result.body.details));
      assert.doesNotMatch(JSON.stringify(result.body), /private-invalid-token|stackTrace|api_secret/);
    }
    const anonymous = await client.page.evaluate(async () => {
      const response = await fetch('/api/cart');
      return { status: response.status, body: await response.json() };
    });
    contract(anonymous, 401, 'UNAUTHORIZED');
    const expired = await client.page.evaluate(async () => {
      const response = await fetch('/api/orders/my', { headers: { Authorization: 'Bearer private-invalid-token' } });
      return { status: response.status, body: await response.json() };
    });
    contract(expired, 401, 'UNAUTHORIZED');
    const csrf = await client.page.evaluate(async () => {
      const response = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
      return { status: response.status, body: await response.json() };
    });
    contract(csrf, 403, 'FORBIDDEN');
    const product = await api(client.page, '/products/search?minPrice=-1');
    contract(product, 400, 'VALIDATION_FAILED');
    assert.equal(typeof product.body.details.minPrice, 'string');
    const binding = await api(client.page, '/products/search?page=private-rejected-value');
    contract(binding, 400, 'VALIDATION_FAILED');
    assert.doesNotMatch(JSON.stringify(binding.body), /private-rejected-value/);
    const registration = await api(client.page, '/auth/register', 'POST', {
      fullName: '', username: '', email: 'invalid-email', password: 'x', role: 'CLIENT',
    });
    contract(registration, 400, 'VALIDATION_FAILED');
    assert.equal(typeof registration.body.details.email, 'string');
    const order = await api(client.page, `/orders/${delivered.id}/archive`, 'PATCH', {});
    contract(order, 400, 'VALIDATION_FAILED');
    assert.equal(typeof order.body.details.archived, 'string');
    contract(await api(stranger.page, `/orders/${delivered.id}`), 403, 'FORBIDDEN');
    contract(await api(client.page, '/media/images/000000000000000000000000/metadata'), 404, 'NOT_FOUND');
    contract(await api(client.page, '/orders/000000000000000000000000'), 404, 'NOT_FOUND');
    contract(await api(client.page, `/unknown-route-${keyword}`), 404, 'NOT_FOUND');
  });

  scenario('seller profile opens the image picker and saves the studio handle', async () => {
    const page = seller.page;
    const name = `${keyword} Studio`;
    await page.goto(`${baseUrl}/profile`, { waitUntil: 'networkidle0' });
    await clickText(page, 'button', 'Edit studio bio', false);
    const [chooser] = await Promise.all([
      page.waitForFileChooser(),
      page.locator('#profileAvatar').click(),
    ]);
    await chooser.accept([]);
    await page.locator('#profileUsername').fill(name);
    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith('/api/me') && r.request().method() === 'PUT'),
      clickText(page, 'button[type="submit"]', 'Save modifications'),
    ]);
    assert.equal(response.status(), 200);
    assert.equal((await response.json()).username, name);
    await page.waitForSelector('app-update-profile-dialog', { hidden: true });
    await page.reload({ waitUntil: 'networkidle0' });
    assert.equal(await page.$eval('.maker-name', element => element.textContent.trim()), name);
  });

});

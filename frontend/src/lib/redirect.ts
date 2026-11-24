/**
 * Sends the browser to another page (the provider's checkout). Kept in its own module so tests can replace it, because
 * jsdom does not navigate.
 */
export function redirectTo(url: string): void {
  window.location.assign(url)
}

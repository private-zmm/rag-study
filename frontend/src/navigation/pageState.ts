import type { PageKey } from '../types';

const ACTIVE_PAGE_KEY = 'rag-study-active-page';
const pageKeys = new Set<PageKey>(['chat', 'search', 'notes', 'knowledge', 'clipper', 'settings']);

export function getStoredActivePage(): PageKey {
  const storedPage = localStorage.getItem(ACTIVE_PAGE_KEY);

  if (storedPage && pageKeys.has(storedPage as PageKey)) {
    return storedPage as PageKey;
  }

  return 'chat';
}

export function storeActivePage(page: PageKey) {
  localStorage.setItem(ACTIVE_PAGE_KEY, page);
}

export function clearStoredActivePage() {
  localStorage.removeItem(ACTIVE_PAGE_KEY);
}

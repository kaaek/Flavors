import { useMemo, useState } from 'react'
import { searchBrands, type BrandSearchParams } from '../api/brandSearch'
import { Icon } from '../layout/Icon'
import { Pager } from '../layout/Pager'
import { useDebounced } from '../lib/useDebounced'
import { useSearchResults } from '../lib/useSearchResults'

const PAGE_SIZE = 20

interface Form {
  q: string
  cuisine: string
  location: string
  page: number
}
const emptyForm: Form = { q: '', cuisine: '', location: '', page: 0 }

const filterLabels = { cuisine: 'Cuisine', location: 'Location' } as const
type FilterKey = keyof typeof filterLabels

function toParams(form: Form): BrandSearchParams {
  return {
    q: form.q.trim(),
    cuisine: form.cuisine.trim(),
    location: form.location.trim(),
    page: form.page,
    size: PAGE_SIZE,
  }
}

/** A creator looks for restaurant brands to work with: one plain search bar plus cuisine and branch-location filters. */
export function BrandSearchPage() {
  const [form, setForm] = useState<Form>(emptyForm)
  const [advanced, setAdvanced] = useState(false)

  const debounced = useDebounced(form)
  const params = useMemo(() => toParams(debounced), [debounced])
  const results = useSearchResults(params, searchBrands)

  // Searching for something new goes back to page one; only the pager changes `page` alone.
  function change(patch: Partial<Form>) {
    setForm((current) => ({ ...current, ...patch, page: 0 }))
  }

  const activeFilters = (Object.keys(filterLabels) as FilterKey[]).filter(
    (key) => form[key].trim() !== '',
  )
  const hasCriteria = form.q.trim() !== '' || activeFilters.length > 0

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Find brands</h1>
          <p>Search restaurant brands by name, cuisine or city.</p>
        </div>
      </header>

      <div className="fl-card fl-stack">
        <div className="fl-field">
          <label className="fl-label" htmlFor="brand-search-q">
            Search brands
          </label>
          <input
            className="fl-input"
            id="brand-search-q"
            type="search"
            placeholder="Search by name, cuisine or city… e.g. burgers beirut"
            value={form.q}
            onChange={(event) => change({ q: event.target.value })}
          />
        </div>

        <div className="fl-row">
          <button
            className="fl-btn fl-btn--outline fl-btn--sm"
            type="button"
            aria-expanded={advanced}
            aria-controls="brand-advanced"
            onClick={() => setAdvanced((open) => !open)}
          >
            Advanced
          </button>
          {activeFilters.map((key) => (
            <span className="fl-chip fl-chip--info" key={key}>
              {filterLabels[key]}: {form[key]}
              <button
                className="fl-iconbtn"
                type="button"
                aria-label={`Remove ${filterLabels[key]} filter`}
                onClick={() => change({ [key]: '' })}
              >
                <Icon name="x" />
              </button>
            </span>
          ))}
          {hasCriteria && (
            <button
              className="fl-btn fl-btn--ghost fl-btn--sm"
              type="button"
              onClick={() => setForm(emptyForm)}
            >
              Clear all filters
            </button>
          )}
        </div>

        {advanced && (
          <div id="brand-advanced" className="fl-stats">
            {(Object.keys(filterLabels) as FilterKey[]).map((key) => (
              <div className="fl-field" key={key}>
                <label className="fl-label" htmlFor={`brand-filter-${key}`}>
                  {filterLabels[key]}
                </label>
                <input
                  className="fl-input"
                  id={`brand-filter-${key}`}
                  value={form[key]}
                  onChange={(event) => change({ [key]: event.target.value })}
                />
              </div>
            ))}
          </div>
        )}
      </div>

      {results.status === 'error' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load brands. Please try again.</div>
        </div>
      )}

      {results.status === 'loading' && (
        <div className="fl-card" aria-busy="true" aria-label="Loading brands">
          <div className="fl-skel fl-skel--line" />
        </div>
      )}

      {results.status === 'ready' && results.data.items.length === 0 && (
        <div className="fl-card fl-empty">
          <h2>No brands found</h2>
          <p>Try loosening a filter or checking the spelling of your search.</p>
          {hasCriteria && (
            <button
              className="fl-btn fl-btn--outline"
              type="button"
              onClick={() => setForm(emptyForm)}
            >
              Clear all filters
            </button>
          )}
        </div>
      )}

      {results.status === 'ready' && results.data.items.length > 0 && (
        <>
          <div className="fl-stats fl-stats--wide">
            {results.data.items.map((brand) => (
              <article className="fl-card fl-stack" key={brand.brandId}>
                <h2 className="fl-card__title">{brand.businessName}</h2>
                {brand.cuisine && <span className="fl-chip fl-chip--neutral">{brand.cuisine}</span>}
                {brand.description && <p>{brand.description}</p>}
                {brand.branchLocations.length > 0 && (
                  <p className="fl-muted">Branches: {brand.branchLocations.join(', ')}</p>
                )}
              </article>
            ))}
          </div>
          <Pager
            page={results.data.page}
            size={results.data.size}
            total={results.data.totalItems}
            onPage={(page) => setForm((current) => ({ ...current, page }))}
          />
        </>
      )}
    </div>
  )
}

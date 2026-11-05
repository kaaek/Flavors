/** Previous/Next under a result table; `page` is zero-based, as in the API. */
export function Pager({
  page,
  size,
  total,
  onPage,
}: {
  page: number
  size: number
  total: number
  onPage: (page: number) => void
}) {
  const from = page * size + 1
  const to = Math.min(total, (page + 1) * size)
  return (
    <div className="fl-pager">
      <span>
        {from}–{to} of {total}
      </span>
      <div className="fl-row">
        <button
          className="fl-btn fl-btn--outline fl-btn--sm"
          type="button"
          disabled={page === 0}
          onClick={() => onPage(page - 1)}
        >
          Previous
        </button>
        <button
          className="fl-btn fl-btn--outline fl-btn--sm"
          type="button"
          disabled={to >= total}
          onClick={() => onPage(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  )
}

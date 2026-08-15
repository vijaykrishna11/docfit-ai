function ProviderCardSkeleton() {
  return (
    <li className="provider-card skeleton-card">
      <div className="provider-card-top">
        <div className="avatar skeleton-block" />
        <div className="provider-card-heading">
          <div className="skeleton-block skeleton-line skeleton-line-title" />
          <div className="skeleton-block skeleton-line skeleton-line-badge" />
        </div>
        <div className="skeleton-block skeleton-pill" />
      </div>
      <div className="provider-card-details">
        <div className="skeleton-block skeleton-line" />
        <div className="skeleton-block skeleton-line skeleton-line-short" />
      </div>
      <div className="provider-card-actions">
        <div className="skeleton-block skeleton-line skeleton-line-checkbox" />
        <div className="skeleton-actions-row">
          <div className="skeleton-block skeleton-button" />
          <div className="skeleton-block skeleton-button" />
          <div className="skeleton-block skeleton-button" />
        </div>
      </div>
    </li>
  )
}

export default ProviderCardSkeleton

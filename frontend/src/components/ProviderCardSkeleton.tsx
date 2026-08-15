function ProviderCardSkeleton() {
  return (
    <li className="provider-card skeleton-card">
      <div className="provider-card-top">
        <div className="avatar skeleton-block" />
        <div className="provider-card-heading">
          <div className="skeleton-block skeleton-line skeleton-line-title" />
          <div className="skeleton-block skeleton-line skeleton-line-badge" />
        </div>
      </div>
      <div className="provider-card-details">
        <div className="skeleton-block skeleton-line" />
        <div className="skeleton-block skeleton-line skeleton-line-short" />
      </div>
    </li>
  )
}

export default ProviderCardSkeleton

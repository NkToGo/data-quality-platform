const plannedCapabilities = [
  'Dataset management and CSV ingestion',
  'Configurable validation rules and issue tracking',
  'Run summaries, issue filtering, and report export',
];

function App() {
  return (
    <>
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="site-header">
        <div className="content-width">
          <p className="milestone-label">Milestone 1</p>
          <h1>Data Quality Platform</h1>
          <p className="intro">
            A foundation for building transparent, testable data-quality workflows in later
            milestones.
          </p>
        </div>
      </header>

      <main id="main-content" className="content-width">
        <section className="panel" aria-labelledby="foundation-heading">
          <p className="status">
            <span className="status-indicator" aria-hidden="true" />
            Foundation available
          </p>
          <h2 id="foundation-heading">Project foundation</h2>
          <p>
            The React application shell, Spring Boot service, local PostgreSQL configuration, and
            automated checks are in place. PostgreSQL is not connected to the backend yet.
          </p>
        </section>

        <section className="panel" aria-labelledby="planned-heading">
          <h2 id="planned-heading">Planned for later milestones</h2>
          <ul>
            {plannedCapabilities.map((capability) => (
              <li key={capability}>{capability}</li>
            ))}
          </ul>
          <p className="planned-note">These workflows are not available in Milestone 1.</p>
        </section>
      </main>

      <footer className="site-footer">
        <div className="content-width">
          <p>Learning and software-engineering project</p>
        </div>
      </footer>
    </>
  );
}

export default App;

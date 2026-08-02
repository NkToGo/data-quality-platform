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
          <p className="milestone-label">Project status</p>
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
            automated checks are in place. PostgreSQL-backed Dataset, Validation Profile, Validation
            Rule, SourceFile upload, and Validation Run creation, list, and detail APIs are
            available in the backend, together with Validation Issue retrieval. Creating a
            Validation Run synchronously parses and validates its private stored CSV bytes. Normal
            processing persists Issues and summary counters as COMPLETED, while expected or
            recovered processing failures record safe FAILED outcomes. Unexpected server failures
            can retain a durable PENDING Run. The frontend shell does not call these APIs or provide
            Validation Run screens yet.
          </p>
        </section>

        <section className="panel" aria-labelledby="planned-heading">
          <h2 id="planned-heading">Planned frontend capabilities</h2>
          <ul>
            {plannedCapabilities.map((capability) => (
              <li key={capability}>{capability}</li>
            ))}
          </ul>
          <p className="planned-note">These workflows are not available in the frontend yet.</p>
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

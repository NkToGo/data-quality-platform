import { Link, Route, Routes, useParams } from 'react-router-dom';
import { DashboardPage } from './pages/DashboardPage';
import { ValidationRunDetailPage } from './pages/ValidationRunDetailPage';

function ValidationRunRoute() {
  const { runId } = useParams();

  return runId === undefined ? <NotFoundRoute /> : <ValidationRunDetailPage runId={runId} />;
}

function NotFoundRoute() {
  return (
    <section className="panel" aria-labelledby="not-found-heading">
      <h2 id="not-found-heading">Page not found</h2>
      <p>The requested frontend route does not exist.</p>
      <Link to="/">Return to dashboard</Link>
    </section>
  );
}

function App() {
  return (
    <>
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="site-header">
        <div className="content-width">
          <p className="milestone-label">Milestone 6</p>
          <h1>
            <Link to="/">Data Quality Platform</Link>
          </h1>
          <p className="intro">
            Read-only Dataset and Validation Run dashboard with report exports.
          </p>
        </div>
      </header>

      <main id="main-content" className="content-width">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/runs/:runId" element={<ValidationRunRoute />} />
          <Route path="*" element={<NotFoundRoute />} />
        </Routes>
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

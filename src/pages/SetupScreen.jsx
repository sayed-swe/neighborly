export default function SetupScreen() {
  return (
    <div className="setup">
      <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 36, marginBottom: 8 }}>Neighborly</h1>
      <p className="muted mb-16">The app isn't connected to Supabase yet.</p>
      <div className="card card-pad">
        <ol style={{ paddingLeft: 18, margin: 0, lineHeight: 1.8 }}>
          <li>Create a project at <a className="link" href="https://supabase.com/dashboard" target="_blank" rel="noreferrer">supabase.com</a>.</li>
          <li>Run the SQL files in <code>supabase/migrations/</code> (in order) in the SQL editor. Optionally run <code>supabase/seed_demo.sql</code>.</li>
          <li>Copy <code>.env.example</code> to <code>.env</code> and paste your project URL and anon key:</li>
        </ol>
        <pre>{`VITE_SUPABASE_URL=https://xxxx.supabase.co\nVITE_SUPABASE_ANON_KEY=eyJhbGciOi...`}</pre>
        <p className="mt-12">Then restart <code>npm run dev</code>.</p>
      </div>
    </div>
  )
}

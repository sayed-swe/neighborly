import { Bell, CalendarDays, HandHeart, Map, PackageSearch, Tag } from 'lucide-react'
import { Brand } from '../components/Sidebar'

export default function AuthHero() {
  const features = [
    [Map, 'See what’s happening nearby'],
    [Tag, 'Buy & sell with neighbors'],
    [HandHeart, 'Ask for and offer help'],
    [PackageSearch, 'Lost & found'],
    [CalendarDays, 'Local events'],
    [Bell, 'Emergency alerts'],
  ]
  return (
    <div className="auth-hero">
      <Brand />
      <div>
        <h1>Your neighborhood, connected.</h1>
        <p>Neighborly is a private space for the people who live around you — share updates, solve local problems and help each other out.</p>
      </div>
      <div className="features">
        {features.map(([Icon, text]) => (
          <div key={text} className="feature"><Icon size={20} /> {text}</div>
        ))}
      </div>
    </div>
  )
}

import { Ban } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Empty } from '../components/ui'

export default function Blocked({ status }) {
  const { signOut } = useAuth()
  return (
    <div className="setup">
      <div className="card">
        <Empty icon={Ban}
          title={status === 'banned' ? 'Your account has been banned' : 'Your account is suspended'}
          text="This happened after a moderator reviewed reports about your activity. Contact the Neighborly admins if you think this is a mistake."
          action={<button className="btn btn-outline" onClick={signOut}>Log out</button>} />
      </div>
    </div>
  )
}

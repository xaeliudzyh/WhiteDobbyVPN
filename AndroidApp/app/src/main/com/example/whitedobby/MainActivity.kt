package app
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация Firebase Auth
        auth = Firebase.auth
    }
}

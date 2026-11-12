package com.greengauge

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.greengauge.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var viewModel: AppViewModel
    private var googleSignInClient: GoogleSignInClient? = null
    private var isSignUpMode = false
    private var hasNavigatedToMain = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.signInWithGoogle(idToken)
            } else {
                Toast.makeText(
                    this,
                    "Google Sign-In did not return an ID token. Check Firebase OAuth Web Client ID.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(
                this,
                "Google Sign-In failed (code ${e.statusCode}): ${e.localizedMessage ?: "Canceled"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (FirebaseAuth.getInstance().currentUser != null) {
            navigateToMain()
            return
        }

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]
        setupGoogleSignIn()
        setupListeners()
        observeViewModel()
        updateModeUi()
    }

    private fun setupGoogleSignIn() {
        val clientId = getString(R.string.default_web_client_id).trim()
        if (clientId.endsWith(".apps.googleusercontent.com")) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } else {
            googleSignInClient = null
        }
    }

    private fun setupListeners() {
        binding.etName.doAfterTextChanged { binding.tilName.error = null }
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }

        binding.btnAuth.setOnClickListener {
            submitAuthForm()
        }

        binding.tvToggleMode.setOnClickListener {
            isSignUpMode = !isSignUpMode
            clearFieldErrors()
            updateModeUi()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            val client = googleSignInClient
            if (client == null) {
                Toast.makeText(
                    this,
                    "Google Sign-In requires a valid OAuth 2.0 Web Client ID in google-services.json / strings.xml.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                googleSignInLauncher.launch(client.signInIntent)
            }
        }
    }

    private fun updateModeUi() {
        if (isSignUpMode) {
            binding.tvAuthSubtitle.text = "Create your sustainability account"
            binding.tilName.visibility = View.VISIBLE
            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.btnAuth.text = "Create Account"
            binding.tvToggleMode.text = "Already have an account? Sign In"
            binding.tvForgotPassword.visibility = View.GONE
        } else {
            binding.tvAuthSubtitle.text = "Sign in to continue your eco journey"
            binding.tilName.visibility = View.GONE
            binding.tilConfirmPassword.visibility = View.GONE
            binding.btnAuth.text = "Sign In"
            binding.tvToggleMode.text = "Don't have an account? Sign Up"
            binding.tvForgotPassword.visibility = View.VISIBLE
        }
    }

    private fun clearFieldErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun submitAuthForm() {
        clearFieldErrors()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        var valid = true
        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.error = "Enter a valid email address"
            valid = false
        }
        if (!ValidationUtils.isValidPassword(password)) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            valid = false
        }

        if (isSignUpMode) {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
            if (!ValidationUtils.isValidName(name)) {
                binding.tilName.error = "Name must be 2-50 characters"
                valid = false
            }
            if (confirmPassword != password) {
                binding.tilConfirmPassword.error = "Passwords do not match"
                valid = false
            }
            if (valid) {
                viewModel.registerUser(email, password, name)
            }
        } else if (valid) {
            viewModel.authenticateUser(email, password)
        }
    }

    private fun showForgotPasswordDialog() {
        val input = TextInputEditText(this).apply {
            hint = "Enter your account email"
            setText(binding.etEmail.text?.toString()?.trim().orEmpty())
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("We will send a password reset link to your email address.")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text?.toString()?.trim().orEmpty()
                if (!ValidationUtils.isValidEmail(email)) {
                    Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.sendPasswordReset(email)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnAuth.isEnabled = !loading
            binding.btnGoogleSignIn.isEnabled = !loading
        }

        viewModel.isAuthenticated.observe(this) { authenticated ->
            if (authenticated == true && !hasNavigatedToMain) {
                navigateToMain()
            }
        }

        viewModel.passwordResetSent.observe(this) { sent ->
            if (sent == true) {
                Toast.makeText(
                    this,
                    "Password reset email sent. Please check your inbox.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearPasswordResetSent()
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrBlank()) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun navigateToMain() {
        if (hasNavigatedToMain) return
        hasNavigatedToMain = true
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

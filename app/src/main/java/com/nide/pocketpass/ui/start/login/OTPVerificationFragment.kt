package com.nide.pocketpass.ui.start.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nide.pocketpass.R
import com.nide.pocketpass.data.local.util.LocalDataStore
import com.nide.pocketpass.data.local.util.userDataStore
import com.nide.pocketpass.data.module.User
import com.nide.pocketpass.data.remote.util.DatabaseConstant.USER_DB
import com.nide.pocketpass.databinding.FragmentOTPVerificationBinding
import com.nide.pocketpass.ui.MainActivity
import com.nide.pocketpass.ui.start.login.LoginOrRegisterFragment.Companion.countryCode
import com.nide.pocketpass.ui.start.login.LoginOrRegisterFragment.Companion.isLogin
import com.nide.pocketpass.ui.start.login.LoginOrRegisterFragment.Companion.phone
import com.nide.pocketpass.ui.start.login.LoginOrRegisterFragment.Companion.userName
import com.nide.pocketpass.ui.start.login.LoginOrRegisterFragment.Companion.verificationCode
import com.nide.pocketpass.ui.start.login.LoginOrRegisterFragment.Companion.verificationId
import com.nide.pocketpass.util.collectLatestFlow
import com.nide.pocketpass.util.showSoftKeyboard
import kotlinx.coroutines.*

class OTPVerificationFragment : Fragment() {

    private val TAG = javaClass.simpleName
    private var _binding: FragmentOTPVerificationBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore

    private val viewModel: LoginViewModel by viewModels()


    private val onBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(resources.getString(R.string.back_alert_title))
                setMessage(resources.getString(R.string.supporting_text))
                setNegativeButton(resources.getString(R.string.decline)) { dialog, which ->
                    // Respond to negative button press
                    dialog.dismiss()

                }
                setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->
                    // Respond to positive button press
                    findNavController().navigateUp()
                }
            }.show()
        }
    }


    private var mCallback: PhoneAuthProvider.OnVerificationStateChangedCallbacks =
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(authCredential: PhoneAuthCredential) {
                binding.etOtp.setText(authCredential.smsCode ?: "")
                Log.i(TAG, "onVerificationCompleted: ${authCredential.smsCode}")

            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.i(TAG, "onVerificationFailed: ${e.message} :=> $e")
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }


            override fun onCodeSent(
                verificationid: String,
                forceResendingToken: PhoneAuthProvider.ForceResendingToken
            ) {
                super.onCodeSent(verificationid, forceResendingToken)
                Toast.makeText(context, "resend OTP successfully ", Toast.LENGTH_SHORT).show()

            }
        }


    override fun onDestroy() {
        super.onDestroy()

        _binding = null
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        viewModel.startCountDown()
        _binding = FragmentOTPVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(onBackCallback)
        binding.etOtp.showSoftKeyboard()
        initViews()
        initClicks()
    }


    private fun initClicks() = binding.apply {
        etOtp.doOnTextChanged { text, start, before, count ->
            btnVerification.isEnabled = text.toString().length >= 6
        }
        btnVerification.setOnClickListener {
            if (etOtp.text.toString().length < 6) {
                Toast.makeText(context, "Please enter OTP ", Toast.LENGTH_SHORT).show()
            } else {
                verifyCode(etOtp.text.toString())
            }
        }

        btnResend.setOnClickListener {
            viewModel.startCountDown()
            viewModel.sendOtp(countryCode + phone, mCallback, requireActivity())
        }


    }

    private fun initViews() = binding.apply {
        if (verificationCode.isNotBlank()) {
            etOtp.setText(verificationCode)
        }

        tvPhoneDetails.text = resources.getString(R.string.phone_details_text, "$countryCode $phone")

        collectLatestFlow(viewModel.timerText) { time ->
            btnResend.isEnabled = time == 0
            tvTimeText.isVisible = time != 0
            tvTimeText.text = resources.getString(R.string.otp_resend_text, time)
        }


    }

    private fun verifyCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneAuthCredential(credential)
    }


    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    if (isLogin) {
                        lifecycleScope.launch {
                            getUserData(phone)
                        }

                    } else {
                        val fuser = task.result?.user
                        val user = User(
                            userId = fuser!!.uid,
                            userName = userName,
                            userPhone = phone,
                            countryCode = countryCode
                        )
                        lifecycleScope.launch {
                            addNewUser(user)
                        }
                    }


                } else {

                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        // The verification code entered was invalid
                        Toast.makeText(context, "Please enter a valid OTP", Toast.LENGTH_SHORT)
                            .show()
                    }

                }
            }
    }


    private suspend fun addNewUser(user: User) =
        withContext(lifecycleScope.coroutineContext + Dispatchers.IO) {
            db.collection(USER_DB).document(user.userId)
                .set(user)
                .addOnSuccessListener {
                    runBlocking {
                        addLocalUser(user)
                    }
                    Intent(requireContext(), MainActivity::class.java).apply {
                        startActivity(this)
                        requireActivity().finish()
                    }

                }
                .addOnFailureListener {
                    Toast.makeText(context, "something went go wrong !", Toast.LENGTH_SHORT).show()
                }
        }


    private suspend fun addLocalUser(user: User) {
        Log.i(TAG, "addLocalUser: $user")
        context?.userDataStore?.edit { setting ->
            setting[LocalDataStore.isUserLogin_Key] = true
            setting[LocalDataStore.userName_Key] = user.userName
            setting[LocalDataStore.userEmail_Key] = user.userEmail
            setting[LocalDataStore.userPhone_key] = user.userPhone
            setting[LocalDataStore.userCountryCode_Key] = user.countryCode
        }

    }


    private suspend fun getUserData(mobile: String) =
        withContext(lifecycleScope.coroutineContext + Dispatchers.IO) {
            db.collection(USER_DB)
                .whereEqualTo("userPhone", mobile)
                .get()
                .addOnSuccessListener { user ->
                    val localUser = user.toObjects(User::class.java)
                    runBlocking {
                        addLocalUser(localUser.first())
                    }
                    Intent(requireContext(), MainActivity::class.java).apply {
                        startActivity(this)
                        requireActivity().finish()
                    }

                }
                .addOnFailureListener {
                    Toast.makeText(context, "something went go wrong", Toast.LENGTH_SHORT).show()
                }

        }

}
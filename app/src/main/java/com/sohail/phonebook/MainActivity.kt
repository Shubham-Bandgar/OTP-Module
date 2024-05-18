package com.sohail.phonebook

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.sohail.phonebook.ui.theme.PhoneBookTheme
import com.sohail.phonebook.utils.getActivity
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneBookTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "login") {
                    composable("login") { LoginDialog(navController) }
                    composable("secondPage") { SecondPage() }
                }
            }
        }
    }
}

@Composable
fun LoginDialog(navController: NavController) {
    val dialogState: MutableState<Boolean> = remember {
        mutableStateOf(true)
    }
    Dialog(
        onDismissRequest = { dialogState.value = false },
        content = {
            CompleteDialogContent(navController)
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

val auth = FirebaseAuth.getInstance()
var storedVerificationId: String = ""
var isLoggedIn = false

@Composable
fun CompleteDialogContent(navController: NavController) {
    val context = LocalContext.current
    var phoneNumber by remember {
        mutableStateOf(TextFieldValue(""))
    }
    var otp by remember {
        mutableStateOf(TextFieldValue(""))
    }
    var isOtpVisible by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .height(300.dp)
            .fillMaxWidth(1f)
            .wrapContentHeight(),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(1f)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Login with phone number", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextField(
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.White
                ),
                placeholder = { Text("Enter phone number") },
                value = phoneNumber,
                onValueChange = {
                    if (it.text.length <= 10) phoneNumber = it
                },
                modifier = Modifier
                    .fillMaxWidth(1f)
                    .padding(top = 4.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if(isOtpVisible) {
                TextField(
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.White
                    ),
                    value = otp,
                    placeholder = { Text("Enter otp") },
                    onValueChange = { otp = it },
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .padding(top = 4.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            if(!isOtpVisible) {
                Button(
                    onClick = { onLoginClicked(context, phoneNumber.text) {
                        Log.d("phoneBook","setting otp visible")
                        isOtpVisible = true
                    }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .padding(top = 8.dp)
                ) {
                    Text(text = "Send otp", color = Color.White)
                }
            }

            if(isOtpVisible) {
                Button(
                    onClick = { verifyPhoneNumberWithCode(context, storedVerificationId, otp.text, navController) },
                    colors = ButtonDefaults.textButtonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .padding(top = 8.dp)
                ) {
                    Text(text = "Verify", color = Color.White)
                }
            }
        }
    }
}

private fun verifyPhoneNumberWithCode(context: Context, verificationId: String, code: String, navController: NavController) {
    if (isLoggedIn) {
        Toast.makeText(context, "Already logged in", Toast.LENGTH_SHORT).show()
        return
    }

    val sharedPreferences = context.getSharedPreferences("OTP", Context.MODE_PRIVATE)
    val otpSentTime = sharedPreferences.getLong("otpSentTime", 0)
    val currentTime = System.currentTimeMillis()
    val otpExpirationTime = 10 * 60 * 1000 // 10 minutes

    if (currentTime - otpSentTime > otpExpirationTime) {
        Log.d("phoneBook", "OTP has expired")
        Toast.makeText(context, "OTP has expired", Toast.LENGTH_SHORT).show()
        return
    }

    val credential = PhoneAuthProvider.getCredential(verificationId, code)
    signInWithPhoneAuthCredential(context, credential, navController)
}

fun onLoginClicked(context: Context, phoneNumber: String, onCodeSent: () -> Unit) {
    auth.setLanguageCode("en")
    val callback = object: PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d("phoneBook", "verification completed")
            signInWithPhoneAuthCredential(context, credential, null)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.d("phoneBook", "verification failed: $e")
            Toast.makeText(context, "Verification failed", Toast.LENGTH_SHORT).show()
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            Log.d("phoneBook", "code sent: $verificationId")
            storedVerificationId = verificationId
            val sharedPreferences = context.getSharedPreferences("OTP", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("verificationId", verificationId)
                putLong("otpSentTime", System.currentTimeMillis())
                apply()
            }
            onCodeSent()
        }
    }

    val options = context.getActivity()?.let {
        PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91$phoneNumber")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(it)
            .setCallbacks(callback)
            .build()
    }
    if (options != null) {
        Log.d("phoneBook", options.toString())
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}

private fun signInWithPhoneAuthCredential(context: Context, credential: PhoneAuthCredential, navController: NavController?) {
    context.getActivity()?.let {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(it) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = task.result?.user
                    Log.d("phoneBook", "logged in")
                    Toast.makeText(context, "Logged in successfully", Toast.LENGTH_SHORT).show()
                    isLoggedIn = true
                    navController?.navigate("secondPage")
                } else {
                    // Sign in failed, display a message and update the UI
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        // The verification code entered was invalid
                        Log.d("phoneBook", "wrong OTP")
                        Toast.makeText(context, "Wrong OTP", Toast.LENGTH_SHORT).show()
                    }
                    // Update UI
                }
            }
    }
}

@Composable
fun SecondPage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to AMS", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PhoneBookTheme {
        CompleteDialogContent(rememberNavController())
    }
}
